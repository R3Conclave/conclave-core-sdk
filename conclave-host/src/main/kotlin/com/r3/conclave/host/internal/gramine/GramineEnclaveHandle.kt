package com.r3.conclave.host.internal.gramine

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_TOKEN
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SIGSTRUCT
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.NativeLoader
import com.r3.conclave.host.internal.SocketHostEnclaveInterface
import com.r3.conclave.host.internal.attestation.EnclaveQuoteService
import com.r3.conclave.host.internal.attestation.EnclaveQuoteServiceGramineDCAP
import com.r3.conclave.host.internal.attestation.EnclaveQuoteServiceMock
import com.r3.conclave.host.internal.loggerFor
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.utils.IOUtils
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.io.path.*


class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    private val zipFileUrl: URL
) : EnclaveHandle {

    companion object {
        private val logger = loggerFor<GramineEnclaveHandle>()
        const val GRAMINE_ENTRY_POINT = "java"
        private const val MOCK_MODE_UNSUPPORTED_MESSAGE = "Gramine enclave handle does not support mock mode enclaves"

        private fun getGramineExecutable(enclaveMode: EnclaveMode) =
            when (enclaveMode) {
                EnclaveMode.SIMULATION -> "gramine-direct"
                EnclaveMode.DEBUG -> "gramine-sgx"
                EnclaveMode.RELEASE -> "gramine-sgx"
                EnclaveMode.MOCK -> throw IllegalArgumentException(MOCK_MODE_UNSUPPORTED_MESSAGE)
            }
    }

    private lateinit var gramineProcess: Process

    private val enclaveManifestPath: Path

    private val workingDirectory: Path = Files.createTempDirectory("$enclaveClassName-gramine")

    override val enclaveInterface: SocketHostEnclaveInterface

    override lateinit var quotingService: EnclaveQuoteService

    init {
        require(enclaveMode != EnclaveMode.MOCK) {
            MOCK_MODE_UNSUPPORTED_MESSAGE
        }

        NativeLoader.loadHostLibraries(enclaveMode)
        unzipEnclaveBundle()
        enclaveManifestPath = getManifestFromUnzippedBundle()

        /** Create a socket host interface. */
        enclaveInterface = SocketHostEnclaveInterface()
    }

    override fun initialise(attestationParameters: AttestationParameters?) {
        quotingService = getQuotingService(attestationParameters)

        /** Bind a port for the interface to use. */
        val port = enclaveInterface.bindPort()

        /**
         * Start the enclave process, passing the port that the call interface is listening on.
         * TODO: Implement a *secure* method for passing port to the enclave.
         */
        val command = prepareCommand(port)

        gramineProcess = ProcessBuilder()
            .inheritIO()
            .directory(workingDirectory.toFile())
            .command(command)
            .start()

        // The user should be calling EnclaveHost.close(), but in case they forget, or for some other reason the
        // enclave process hasn't terminated, make sure as a last resort to kill it when the host terminates. This is
        // harmless if the process is already destroyed.
        Runtime.getRuntime().addShutdownHook(Thread(gramineProcess::destroyForcibly))

        /** Wait for the local call interface start process to complete. */
        enclaveInterface.start()

        initializeEnclave(enclaveClassName)
    }

    /** Get the appropriate quoting service. */
    private fun getQuotingService(attestationParameters: AttestationParameters?): EnclaveQuoteService {
        /** Ignore the attestation parameters in simulation mode. */
        if (!enclaveMode.isHardware) {
            return EnclaveQuoteServiceMock
        }

        require(attestationParameters != null)

        /** The gramine runtime does not currently support EPID attestation. */
        return when (attestationParameters) {
            is AttestationParameters.EPID -> throw IllegalArgumentException("EPID attestation is not supported when using the Conclave Gramine runtime.")
            is AttestationParameters.DCAP -> EnclaveQuoteServiceGramineDCAP
        }
    }

    override fun destroy() {
        /** Close the call interface if it's running. */
        if (enclaveInterface.isRunning) {
            enclaveInterface.close()
        }

        /** Wait for the gramine process to terminate if it's running. If it doesn't, destroy it forcibly. */
        if (::gramineProcess.isInitialized) {
            gramineProcess.waitFor(10L, TimeUnit.SECONDS)
            gramineProcess.destroyForcibly()
        }

        /** Clean up temporary files. */
        try {
            workingDirectory.toFile().deleteRecursively()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp directory $workingDirectory", e)
        }
    }

    private fun getManifestFromUnzippedBundle(): Path {
        require((workingDirectory / GRAMINE_ENCLAVE_JAR).exists()) { "Missing enclave jar" }

        return if (enclaveMode == EnclaveMode.SIMULATION) {
            require((workingDirectory / GRAMINE_MANIFEST).exists()) { "Missing Gramine manifest" }
            workingDirectory / GRAMINE_MANIFEST
        } else {
            require((workingDirectory / GRAMINE_SGX_MANIFEST).exists()) { "Missing SGX Gramine manifest" }
            require((workingDirectory / GRAMINE_SIGSTRUCT).exists()) { "Missing SIGSTRUCT file" }
            require((workingDirectory / GRAMINE_SGX_TOKEN).exists()) { "Missing SGX Token" }
            workingDirectory / GRAMINE_SGX_MANIFEST
        }
    }

    private fun isPythonEnclave(): Boolean {
        workingDirectory.toFile().walk().forEach {
            if (it.extension == "py") {
                return true
            }
        }
        return false
    }

    private fun unTarFile(tarGz: Path, outputDir: Path) {
        tarGz.inputStream().use { fis ->
            GZIPInputStream(fis).use { gis ->
                TarArchiveInputStream(gis).use { tis ->
                    var tarEntry = tis.nextTarEntry

                    while (tarEntry != null) {
                        val outputFile = outputDir / tarEntry.name
                        if (tarEntry.isDirectory) {
                            if (!outputFile.exists()) {
                                outputFile.toFile().mkdirs()
                            }
                        } else {
                            outputDir.toFile().mkdirs()
                            FileOutputStream(outputFile.toFile()).use {
                                IOUtils.copy(tis, it)
                            }
                            if (outputFile.toFile().name == GRAMINE_ENTRY_POINT && !isPythonEnclave()) {
                                outputFile.toFile().setExecutable(true)
                            }
                        }
                        tarEntry = tis.nextTarEntry
                    }
                }
            }
        }
    }

    private fun unzipEnclaveBundle() {
        ZipInputStream(zipFileUrl.openStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = workingDirectory.resolve(entry.name)

                if (entry.isDirectory) {
                    path.createDirectories()
                } else {
                    Files.copy(zip, path)
                    if (entry.name.endsWith("tar.gz")) {
                        unTarFile(path, workingDirectory)
                    }
                }
            }
        }
    }

    private fun prepareCommand(port: Int): List<String> {
        val gramineCommand = listOf(getGramineExecutable(enclaveMode))
        val javaCommand = getJavaCommand(port)

        val command = gramineCommand + javaCommand
        logger.debug("Running enclave with command: ${command.joinToString(" ")}")
        return command
    }

    private fun getJavaCommand(port: Int): List<String> {
        return listOf(
            GRAMINE_ENTRY_POINT,
            "-XX:-UseCompressedClassPointers", // TODO CON-1165, we need to understand why this is needed
            "-cp",
            GRAMINE_ENCLAVE_JAR,
            "com.r3.conclave.enclave.internal.GramineEntryPoint",
            port.toString()
        )
    }

    override val mockEnclave: Any
        get() {
            throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
        }
}
