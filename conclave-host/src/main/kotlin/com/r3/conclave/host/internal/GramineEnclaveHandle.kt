package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
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
    private lateinit var processGramineDirect: Process

    private val workingDirectory: Path = Files.createTempDirectory("$enclaveClassName-gramine")

    override val enclaveInterface: SocketHostEnclaveInterface

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        unzipIntoWorkingDir()

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
         */
        val command = mutableListOf(
            "gramine-direct",
            "java", "-cp", GRAMINE_ENCLAVE_JAR, "com.r3.conclave.enclave.internal.GramineEntryPoint", port.toString()
        )
        if ((workingDirectory / PYTHON_FILE).exists()) {
            command += PYTHON_FILE
        }

        processGramineDirect = ProcessBuilder()
            .inheritIO()
            .directory(workingDirectory.toFile())
            .command(command)
            .start()

        // The user should be calling EnclaveHost.close(), but in case they forget, or for some other reason the
        // enclave process hasn't terminated, make sure as a last resort to kill it when the host terminates. This is
        // harmless if the process is already destroyed.
        Runtime.getRuntime().addShutdownHook(Thread(processGramineDirect::destroyForcibly))

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
        if (::processGramineDirect.isInitialized) {
            processGramineDirect.waitFor(10L, TimeUnit.SECONDS)
            processGramineDirect.destroyForcibly()
        }

        /** Clean up temporary files. */
        try {
            workingDirectory.toFile().deleteRecursively()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp directory $workingDirectory", e)
        }
    }

    private fun unzipIntoWorkingDir() {
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
        require((workingDirectory / GRAMINE_MANIFEST).exists()) { "Missing gramine manifest" }
        require((workingDirectory / GRAMINE_ENCLAVE_JAR).exists()) { "Missing enclave jar" }
    }

    /**
     * Retrieves the thread count number by parsing the manifest.
     * This is bit hacky but will do for now.
     * TODO: Implement a proper method for providing build-time enclave meta-data to the host before enclave startup
     */
    private fun getEnclaveThreadCountFromManifest(): Int {
        var inCorrectSection = false

        (workingDirectory / GRAMINE_MANIFEST).reader().use {
            for (line in it.readLines()) {
                val tokens = line.trim().split("=").map { token -> token.trim() }

                if (tokens.size == 1) {
                    inCorrectSection = (tokens[0] == "[sgx]")
                    continue
                }

                if (inCorrectSection && tokens.size >= 2) {
                    if (tokens[0] == "thread_num") {
                        return tokens[1].toInt()
                    }
                }
            }

            throw IllegalStateException("sgx.thread_num missing from manifest, unable to proceed.")
        }
    }

    override val mockEnclave: Any get() {
        throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
    }

    private companion object {
        private val logger = loggerFor<GramineEnclaveHandle>()
    }
}
