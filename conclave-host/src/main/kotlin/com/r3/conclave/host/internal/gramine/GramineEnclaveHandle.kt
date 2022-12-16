package com.r3.conclave.host.internal.gramine

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SECCOMP
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_TOKEN
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SIGSTRUCT
import com.r3.conclave.host.internal.*
import org.tomlj.Toml
import java.io.IOException
import java.lang.IllegalArgumentException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists

class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    private val zipFileUrl: URL

) : EnclaveHandle {

    companion object {
        private val logger = loggerFor<GramineEnclaveHandle>()
        private const val DOCKER_WORKING_DIR = "/project"
        private fun getGramineExecutable(enclaveMode: EnclaveMode) =
            when (enclaveMode) {
                EnclaveMode.SIMULATION -> "gramine-direct"
                EnclaveMode.DEBUG -> "gramine-sgx"
                EnclaveMode.RELEASE -> "gramine-sgx"
                EnclaveMode.MOCK -> throw IllegalArgumentException("MOCK mode is not supported in Gramine")
            }
    }

    private lateinit var gramineProcess: Process

    private val enclaveManifestPath: Path

    private val workingDirectory: Path = Files.createTempDirectory("$enclaveClassName-gramine")

    override val enclaveInterface: SocketHostEnclaveInterface

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        unzipEnclaveBundle()
        enclaveManifestPath = getManifestFromUnzippedBundle()

        /** Create a socket host interface. */
        enclaveInterface = SocketHostEnclaveInterface()
    }

    private fun String.mapWorkingDirectory(): String {
        return this.replace(workingDirectory.toFile().absolutePath, DOCKER_WORKING_DIR).replace("\\", "/")
    }

    override fun initialise() {
        /** Bind a port for the interface to use. */
        val port = enclaveInterface.bindPort()

        /**
         * Start the enclave process, passing the port that the call interface is listening on.
         * TODO: Implement a *secure* method for passing port to the enclave.
         */
        val command = prepareCommandToRun(port)

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

    private fun prepareCommandToRun(port: Int): List<String> {
        val runningImageTag = retrieveImageTagFromManifest()
        val dockerCommand = listOf(
            "docker",
            "run",
            "--network",
            "host",
            "--device=/dev/sgx/enclave",
            "--device=/dev/sgx/provision",
            "-v",
            "/var/run/aesmd:/var/run/aesmd",
            "-v",
            "/dev/sgx_enclave:/dev/sgx_enclave",
            "-v",
            "/dev/sgx_provision:/dev/sgx_provision",
            "-i",
            "--rm",
            "-v",
            "${workingDirectory.toFile().absolutePath}:$DOCKER_WORKING_DIR",
            "-w", workingDirectory.toFile().absolutePath.mapWorkingDirectory(),
            "--security-opt",
            "seccomp=${workingDirectory.toFile().absolutePath}/$GRAMINE_SECCOMP",
            runningImageTag
        )

        val gramineCommand = getGramineExecutable(enclaveMode)

        val javaCommand = listOf(
            "java",
            "-cp",
            GRAMINE_ENCLAVE_JAR,
            "com.r3.conclave.enclave.internal.GramineEntryPoint",
            port.toString()
        )

        println("FInal command: ${(dockerCommand + gramineCommand + javaCommand).joinToString()}")
        return dockerCommand + gramineCommand + javaCommand
    }

    private fun retrieveImageTagFromManifest(): String {
        val manifest = Paths.get(
            if (enclaveMode == EnclaveMode.SIMULATION) "$workingDirectory/$GRAMINE_MANIFEST" else "$workingDirectory/$GRAMINE_SGX_MANIFEST"
        )
        val parseResult = Toml.parse(manifest)
        return parseResult.getString("host.env.CONCLAVE_DOCKER_IMAGE_TAG")!!
    }

    override val mockEnclave: Any
        get() {
            throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
        }
}
