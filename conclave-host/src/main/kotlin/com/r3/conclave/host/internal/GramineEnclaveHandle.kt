package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries

class GramineEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    enclaveManifest: URL,
    override val enclaveClassName: String,
) : EnclaveHandle {
    private val enclaveDirectory: Path
    private val enclaveId: Long
    override val enclaveInterface: HostEnclaveInterface

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        enclaveDirectory = Files.createTempDirectory(enclaveClassName + "gramine").toAbsolutePath()

        val gramineRootDirectory = Paths.get(enclaveManifest.path).parent
        gramineRootDirectory.listDirectoryEntries().forEach { Files.copy(it, enclaveDirectory, REPLACE_EXISTING) }

        //  TODO: We currently support only one Gramine enclave, we need to remove this constraint.
        enclaveId = 1
        enclaveInterface = GramineHostEnclaveInterface(enclaveId)
    }

    override fun initialise() {
        Gramine.start()
    }

    override fun destroy() {
        Gramine.stop()

        Native.destroyEnclave(enclaveId)
        try {
            enclaveDirectory.deleteIfExists()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp directory $enclaveDirectory", e)
        }
    }

    override val mockEnclave: Any get() {
        throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
    }

    private companion object {
        private val logger = loggerFor<GramineEnclaveHandle>()
    }
}
