package com.r3.sgx.core.host.internal

import com.r3.sgx.core.common.LeafSender
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.HandlerConnected
import java.nio.ByteBuffer

class NativeEcallSender<CONNECTION>(private val enclaveId: EnclaveId, handler: Handler<CONNECTION>) : LeafSender() {
    val connection = handler.connect(this)

    init {
        NativeApi.registerOcallHandler(enclaveId, HandlerConnected(handler, connection))
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        val array = ByteArray(serializedBuffer.remaining())
        serializedBuffer.get(array)
        NativeApi.jvmEcall(enclaveId, array)
    }
}
