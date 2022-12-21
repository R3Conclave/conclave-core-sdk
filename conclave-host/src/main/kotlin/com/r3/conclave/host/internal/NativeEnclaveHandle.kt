package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.deleteIfExists

class NativeEnclaveHandle(
    override val enclaveMode: EnclaveMode,
    override val enclaveClassName: String,
    enclaveFileUrl: URL,
) : EnclaveHandle {
    private val enclaveFile: Path
    private val enclaveId: Long
    override val callInterface: NativeHostEnclaveInterface

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        enclaveFile = Files.createTempFile(enclaveClassName, "signed.so").toAbsolutePath()
        enclaveFileUrl.openStream().use { Files.copy(it, enclaveFile, REPLACE_EXISTING) }
        enclaveId = Native.createEnclave(enclaveFile.toString(), enclaveMode != EnclaveMode.RELEASE)
        callInterface = NativeHostEnclaveInterface(enclaveId)
        NativeApi.registerHostEnclaveInterface(enclaveId, callInterface)
    }

    override fun initialise() {
        synchronized(this) {
            initializeEnclave(enclaveClassName)
        }
    }

    override fun destroy() {
        Native.destroyEnclave(enclaveId)
        try {
            enclaveFile.deleteIfExists()
        } catch (e: IOException) {
            logger.debug("Unable to delete temp file $enclaveFile", e)
        }
    }

    override val mockEnclave: Any get() {
        throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
    }

    private companion object {
        private val logger = loggerFor<NativeEnclaveHandle>()
    }
}
