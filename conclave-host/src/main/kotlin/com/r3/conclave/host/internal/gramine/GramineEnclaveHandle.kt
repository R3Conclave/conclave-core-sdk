package com.r3.conclave.host.internal.gramine

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_TOKEN
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SIG
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
import com.r3.conclave.host.internal.EnclaveHandle
import com.r3.conclave.host.internal.SocketHostEnclaveInterface
import com.r3.conclave.host.internal.loggerFor
import org.tomlj.Toml
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.reader

class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    private val zipFileUrl: URL
) : EnclaveHandle {

    companion object {
        private val ENCLAVE_MODE_EXECUTABLES = mapOf(
            EnclaveMode.SIMULATION to "gramine-direct",
            EnclaveMode.DEBUG to "gramine-sgx",
            EnclaveMode.RELEASE to "gramine-sgx"
        )
        private val logger = loggerFor<GramineEnclaveHandle>()
    }

    private lateinit var gramineProcess: Process

    private val enclaveManifestPath: Path

    private val workingDirectory: Path = Files.createTempDirectory("$enclaveClassName-gramine")

    override val enclaveInterface: SocketHostEnclaveInterface

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        unzipEnclaveBundle()
        enclaveManifestPath = getManifestFromUnzippedBundle()

        /**
         * Not all threads in the enclave are necessarily the result of conclave calls.
         * To minimise the likelihood of deadlocks, we don't allow conclave to use all available threads.
         */
        val maxConcurrentCalls = getEnclaveThreadCountFromManifest() / 2
        check(maxConcurrentCalls > 0)

        /** Create a socket host interface. */
        enclaveInterface = SocketHostEnclaveInterface(maxConcurrentCalls)
    }

    override fun initialise() {
        /** Bind a port for the interface to use. */
        val port = enclaveInterface.bindPort()

        /**
         * Start the enclave process, passing the port that the call interface is listening on.
         * TODO: Implement a *secure* method for passing port to the enclave.
         */
        val command = mutableListOf(ENCLAVE_MODE_EXECUTABLES[enclaveMode]!!)
        command += listOf(
            "java",
            "-cp",
            GRAMINE_ENCLAVE_JAR,
            "com.r3.conclave.enclave.internal.GramineEntryPoint",
            port.toString()
        )

        if ((workingDirectory / PYTHON_FILE).exists() && ((workingDirectory / PYTHON_FILE).toFile().length() != 0L)) {
            command += PYTHON_FILE
        }

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

        /** Initialise the enclave. */
        enclaveInterface.initializeEnclave(enclaveClassName)
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
        check(enclaveMode != EnclaveMode.MOCK)

        return if (enclaveMode == EnclaveMode.SIMULATION) {
            require((workingDirectory / GRAMINE_MANIFEST).exists()) { "Missing gramine manifest" }
            require((workingDirectory / GRAMINE_ENCLAVE_JAR).exists()) { "Missing enclave jar" }
            workingDirectory / GRAMINE_MANIFEST
        } else {
            require((workingDirectory / GRAMINE_SGX_MANIFEST).exists()) { "Missing gramine manifest" }
            require((workingDirectory / GRAMINE_ENCLAVE_JAR).exists()) { "Missing enclave jar" }
            require((workingDirectory / GRAMINE_SIG).exists()) { "Missing sig file" }
            require((workingDirectory / GRAMINE_SGX_TOKEN).exists()) { "Missing SGX Token" }
            workingDirectory / GRAMINE_SGX_MANIFEST
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
                }
            }
        }
    }

    /**
     * Retrieves the thread count number by parsing the manifest.
     * This is bit hacky but will do for now.
     * TODO: Implement a proper method for providing build-time enclave meta-data to the host before enclave startup
     */
    private fun getEnclaveThreadCountFromManifest(): Int {
        val result = Toml.parse(enclaveManifestPath)
        val sgxTableSection = result.getTable("sgx")
        checkNotNull(sgxTableSection) { "Could not find the sgx section in the manifest." }
        val numThreads = sgxTableSection.getLong("thread_num")
        checkNotNull(numThreads) { "sgx.thread_num missing from manifest, unable to proceed." }
        return numThreads.toInt()
    }

    override val mockEnclave: Any
        get() {
            throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
        }
}
