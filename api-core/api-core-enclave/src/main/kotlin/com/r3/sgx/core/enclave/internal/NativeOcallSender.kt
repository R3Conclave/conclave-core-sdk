package com.r3.sgx.core.enclave.internal

import com.r3.sgx.core.common.LeafSender
import java.nio.ByteBuffer

object NativeOcallSender : LeafSender() {
    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        val array = ByteArray(serializedBuffer.remaining())
        serializedBuffer.get(array)
        Native.jvmOcall(array)
    }
}
