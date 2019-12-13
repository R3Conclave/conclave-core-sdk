package com.r3.sgx.testing

import com.r3.sgx.core.common.LeafSender
import com.r3.sgx.core.common.HandlerConnected
import java.nio.ByteBuffer

class MockOcallSender<CONNECTION>(val handlerConnected: HandlerConnected<CONNECTION>): LeafSender() {
    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        handlerConnected.onReceive(serializedBuffer)
    }
}
