package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.io.path.div


class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    private val manifestUrl: URL,
    private val jarUrl: URL
) : EnclaveHandle {
    private lateinit var processGramineDirect: Process
    private val enclaveDirectory: Path = Files.createTempDirectory("$enclaveClassName-gramine")

    override val enclaveInterface: SocketHostEnclaveInterface

    init {
        copyGramineFilesToWorkingDirectory()

        /**
         * Not all threads in the enclave are necessarily the result of conclave calls.
         * To minimise the likelihood of deadlocks, we don't allow conclave to use all available threads.
         */
        val maxConcurrentCalls = getEnclaveThreadCountFromManifest() / 2
        check(maxConcurrentCalls > 0)

        /** Create a socket host interface, let the system allocate the port. */
        enclaveInterface = SocketHostEnclaveInterface(0, maxConcurrentCalls)
    }

    companion object {
        const val GRAMINE_ENCLAVE_JAR_NAME = "enclave-shadow.jar"
        const val GRAMINE_ENCLAVE_MANIFEST = "java.manifest"

        private val logger = loggerFor<GramineEnclaveHandle>()
    }

    override fun initialise() {
        /** Start the local call interface. */
        val interfaceStartTask = FutureTask { enclaveInterface.start() }
        val interfaceStartThread = Thread(interfaceStartTask).apply { start() }

        try {
            /** Start the enclave process, passing the port that the call interface is listening on. */
            processGramineDirect = ProcessBuilder()
                .inheritIO()
                .directory(enclaveDirectory.toFile())
                .command("gramine-direct", "java", "-cp", GRAMINE_ENCLAVE_JAR_NAME, "com.r3.conclave.enclave.internal.GramineEntryPoint", enclaveInterface.port.toString())
                .start()

            /** Wait for the local call interface start process to complete. */
            interfaceStartThread.join()     // wait for start process to finish
            interfaceStartTask.get()        // throw if start failed

            /** Initialise the enclave. */
            enclaveInterface.initializeEnclave(enclaveClassName)
        } catch (t: Throwable) {
            this.destroy()
            throw t
        }
    }

    override fun destroy() {

        /** Close the call interface if it's running. */
        if (enclaveInterface.isRunning) {
            enclaveInterface.close()
        }

        /** Wait for the gramine process to terminate if it's running. If it doesn't, destroy it forcibly. */
        if (::processGramineDirect.isInitialized) {
            processGramineDirect.waitFor(10L, TimeUnit.SECONDS)
            if (processGramineDirect.isAlive) {
                processGramineDirect.destroyForcibly()
            }
        }

        /** Clean up temporary files. */
        try {
            enclaveDirectory.toFile().deleteRecursively()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp directory $enclaveDirectory", e)
        }
    }

    private fun copyGramineFilesToWorkingDirectory() {
        //  Here we copy files from inside the jar into a temporary folder

        manifestUrl.openStream().use {
            Files.copy(it, enclaveDirectory / GRAMINE_ENCLAVE_MANIFEST, REPLACE_EXISTING)
        }

        jarUrl.openStream().use {
            Files.copy(it, enclaveDirectory / GRAMINE_ENCLAVE_JAR_NAME, REPLACE_EXISTING)
        }
    }

    /**
     * Retrieves the thread count number by parsing the manifest.
     * This is bit hacky but will do for now.
     * TODO: Implement a proper method for providing build-time enclave meta-data to the host before enclave startup
     */
    private fun getEnclaveThreadCountFromManifest(): Int {
        var inCorrectSection = false

        InputStreamReader(manifestUrl.openStream()).use {
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
}
