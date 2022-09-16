package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.LeafSender
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.deleteIfExists

class NativeEnclaveHandle<CONNECTION>(
    override val enclaveMode: EnclaveMode,
    enclaveFileUrl: URL,
    override val enclaveClassName: String,
    handler: Handler<CONNECTION>
) : EnclaveHandle<CONNECTION>, LeafSender() {
    private val enclaveFile: Path
    private val enclaveId: Long
    override val connection: CONNECTION = handler.connect(this)
    override val callInterface: EnclaveCallInterface

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        enclaveFile = Files.createTempFile(enclaveClassName, "signed.so").toAbsolutePath()
        enclaveFileUrl.openStream().use { Files.copy(it, enclaveFile, REPLACE_EXISTING) }
        enclaveId = Native.createEnclave(enclaveFile.toString(), enclaveMode != EnclaveMode.RELEASE)
        callInterface = NativeEnclaveCallInterface(enclaveId)
        NativeApi.registerOcallHandler(enclaveId, HandlerConnected(handler, connection))
        NativeApi.registerEnclaveCallInterface(enclaveId, callInterface)
    }

    private var initialized = false
    private fun maybeInit() {
        synchronized(this) {
            if (!initialized) {
                callInterface.initializeEnclave(enclaveClassName)
                initialized = true
            }
        }
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        maybeInit()
        NativeApi.hostToEnclave(enclaveId, serializedBuffer.getRemainingBytes(avoidCopying = true))
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
        private val logger = loggerFor<NativeEnclaveHandle<*>>()
    }
}
