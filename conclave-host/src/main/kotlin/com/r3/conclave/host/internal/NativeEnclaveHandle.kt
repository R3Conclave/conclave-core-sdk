package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.LeafSender
import com.r3.conclave.host.internal.fatfs.FileSystemHandler
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

class NativeEnclaveHandle<CONNECTION>(
    override val enclaveMode: EnclaveMode,
    private val enclaveFile: Path,
    private val tempFile: Boolean,
    override val enclaveClassName: String,
    handler: Handler<CONNECTION>
) : EnclaveHandle<CONNECTION>, LeafSender() {
    private val enclaveId: Long
    override val connection: CONNECTION = handler.connect(this)

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        enclaveId = Native.createEnclave(enclaveFile.toAbsolutePath().toString(), enclaveMode != EnclaveMode.RELEASE)
        NativeApi.registerOcallHandler(enclaveId, HandlerConnected(handler, connection))
    }

    private var initialized = false
    private fun maybeInit() {
        synchronized(this) {
            if (!initialized) {
                // The first ECALL has to be the class name of the enclave to be instantiated.
                NativeApi.hostToEnclave(enclaveId, enclaveClassName.toByteArray())
                initialized = true
            }
        }
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        maybeInit()
        NativeApi.hostToEnclave(enclaveId, serializedBuffer.getRemainingBytes())
    }

    override fun destroy() {
        if (tempFile) {
            try {
                enclaveFile.deleteIfExists()
            } catch (e: IOException) {
                // Ignore
            }
        }
        Native.destroyEnclave(enclaveId)
    }

    override val mockEnclave: Any get() {
        throw IllegalStateException("The enclave instance can only be accessed in mock mode.")
    }
}
