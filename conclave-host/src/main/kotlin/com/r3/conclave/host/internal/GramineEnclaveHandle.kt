package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div


class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    private val manifestUrl: URL,
    private val jarUrl: URL
) : EnclaveHandle {
    private lateinit var processGramineDirect: Process
    private val enclaveDirectory: Path
    private val enclaveId: Long
    override val enclaveInterface: HostEnclaveInterface

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        val classNamePath = enclaveClassName.substringAfter("!.")//.replace(".", "/")
        enclaveDirectory = Files.createTempDirectory("$classNamePath-gramine")
        println("Enclave directory $enclaveDirectory")

        NativeLoader.loadHostLibraries(enclaveMode)
        copyGramineFilesToWorkingDirectory()

        //  TODO: We currently support only one Gramine enclave, we need to remove this constraint.
        enclaveId = 1
        enclaveInterface = GramineHostEnclaveInterface(enclaveId)
    }

    override fun initialise() {
        processGramineDirect = ProcessBuilder()
            .inheritIO()
            .directory(enclaveDirectory.toFile())
            .command("gramine-direct", "bash", "-c", """echo "Gramine bash 'enclave' started" && sleep 10000""")
            .start()
    }

    override fun destroy() {
        if (!::processGramineDirect.isInitialized) return
        processGramineDirect.destroy()
        processGramineDirect.waitFor(10L, TimeUnit.SECONDS)
        if (processGramineDirect.isAlive) {
            processGramineDirect.destroyForcibly()
        }

        Native.destroyEnclave(enclaveId)
        try {
            enclaveDirectory.deleteIfExists()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp directory $enclaveDirectory", e)
        }
    }


    private fun copyGramineFilesToWorkingDirectory() {
        val manifestName = File(manifestUrl.file).name
        val jarName = File(jarUrl.file).name

        manifestUrl.openStream().use {
            Files.copy(it, enclaveDirectory / manifestName, REPLACE_EXISTING)
        }

        manifestUrl.openStream().use {
            Files.copy(it, enclaveDirectory / jarName, REPLACE_EXISTING)
        }
    }

    override val mockEnclave: Any
        get() {
            throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
        }

    private companion object {
        private val logger = loggerFor<GramineEnclaveHandle>()
    }
}
