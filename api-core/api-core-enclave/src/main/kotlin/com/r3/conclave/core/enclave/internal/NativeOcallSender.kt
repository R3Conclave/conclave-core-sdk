package com.r3.conclave.core.enclave.internal

import com.r3.conclave.common.internal.getRemainingBytes
import com.r3.conclave.core.common.LeafSender
import java.nio.ByteBuffer

object NativeOcallSender : LeafSender() {
    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        Native.jvmOcall(serializedBuffer.getRemainingBytes())
    }
}
