package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.getRemainingBytes
import com.r3.conclave.common.internal.handler.LeafSender
import java.nio.ByteBuffer

object NativeOcallSender : LeafSender() {
    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        Native.jvmOcall(serializedBuffer.getRemainingBytes())
    }
}
