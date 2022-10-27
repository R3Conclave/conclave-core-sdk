package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.*
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.io.path.div


class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    private val manifestUrl: URL,
    private val jarUrl: URL,
    private val metadataUrl: URL
) : EnclaveHandle {
    private lateinit var processGramineDirect: Process
    private val enclaveDirectory: Path = Files.createTempDirectory("$enclaveClassName-gramine")

    override val enclaveInterface: SocketHostEnclaveInterface

    init {
        copyGramineFilesToWorkingDirectory()

        /** Load gramine enclave metadata. */
        val maxConcurrentCalls = metadataUrl.openStream().use { inputStream ->
            val metadataProperties = Properties().apply { load(inputStream) }
            metadataProperties["maxThreads"]!!.toString().toInt()
        }

        /** Create a socket host interface, let the system allocate the port. */
        enclaveInterface = SocketHostEnclaveInterface(0, maxConcurrentCalls)
    }

    companion object {
        const val GRAMINE_ENCLAVE_JAR_NAME = "enclave-shadow.jar"
        const val GRAMINE_ENCLAVE_MANIFEST = "java.manifest"
        const val GRAMINE_ENCLAVE_METADATA_NAME = "enclave-metadata.properties"

        private val logger = loggerFor<GramineEnclaveHandle>()
    }

    override fun initialise() {
        /** Start the local call interface. */
        val interfaceStartTask = FutureTask { enclaveInterface.start() }
        val interfaceStartThread = Thread(interfaceStartTask).apply { start() }

        try {
            /**
             * Start the enclave process, passing the port that the call interface is listening on.
             * TODO: Implement a *secure* method for passing port to the enclave.
             */
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

        metadataUrl.openStream().use {
            Files.copy(it, enclaveDirectory / GRAMINE_ENCLAVE_METADATA_NAME, REPLACE_EXISTING)
        }
    }

    override val mockEnclave: Any get() {
        throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
    }
}
