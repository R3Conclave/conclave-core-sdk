package com.r3.sgx.core.host.internal

import com.r3.conclave.common.internal.getRemainingBytes
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.HandlerConnected
import com.r3.sgx.core.common.LeafSender
import com.r3.sgx.core.host.EnclaveHandle
import com.r3.sgx.core.host.NativeHostApi
import java.nio.ByteBuffer

class NativeEnclaveHandle<CONNECTION>(
        private val hostApi: NativeHostApi,
        private val enclaveId: EnclaveId,
        handler: Handler<CONNECTION>,
        enclaveClassName: String
) : EnclaveHandle<CONNECTION>, LeafSender() {
    @Volatile
    private var enclaveClassName: String? = enclaveClassName
    private val lock = Any()

    override val connection: CONNECTION = handler.connect(this)

    init {
        NativeApi.registerOcallHandler(enclaveId, HandlerConnected(handler, connection))
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        if (enclaveClassName != null) {
            synchronized(lock) {
                enclaveClassName?.let {
                    // The first ECALL has to be the class name of the enclave to be instantiated.
                    NativeApi.jvmEcall(enclaveId, it.toByteArray())
                }
                enclaveClassName = null
            }
        }
        NativeApi.jvmEcall(enclaveId, serializedBuffer.getRemainingBytes())
    }

    override fun destroy() {
        hostApi.destroyEnclave(enclaveId)
    }
}
