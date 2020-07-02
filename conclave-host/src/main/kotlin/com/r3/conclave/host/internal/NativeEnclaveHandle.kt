package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.LeafSender
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

class NativeEnclaveHandle<CONNECTION>(
        override val enclaveMode: EnclaveMode,
        private val enclaveFile: Path,
        private val tempFile: Boolean,
        enclaveClassName: String,
        handler: Handler<CONNECTION>
) : EnclaveHandle<CONNECTION>, LeafSender() {
    private val enclaveId: Long
    @Volatile
    private var enclaveClassName: String? = enclaveClassName
    private val lock = Any()

    override val connection: CONNECTION = handler.connect(this)

    init {
        require(enclaveMode != EnclaveMode.MOCK)
        NativeLoader.loadHostLibraries(enclaveMode)
        enclaveId = Native.createEnclave(enclaveFile.toAbsolutePath().toString(), enclaveMode != EnclaveMode.RELEASE)
        NativeApi.registerOcallHandler(enclaveId, HandlerConnected(handler, connection))
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        if (enclaveClassName != null) {
            synchronized(lock) {
                enclaveClassName?.let {
                    // The first ECALL has to be the class name of the enclave to be instantiated.
                    NativeApi.hostToEnclave(enclaveId, it.toByteArray())
                }
                enclaveClassName = null
            }
        }
        NativeApi.hostToEnclave(enclaveId, serializedBuffer.getRemainingBytes())
    }

    override fun destroy() {
        if (tempFile) {
            enclaveFile.deleteQuietly()
        }
        Native.destroyEnclave(enclaveId)
    }

    private fun Path.deleteQuietly() {
        try {
            Files.deleteIfExists(this)
        } catch (e: IOException) {
            // Ignore
        }
    }
}
