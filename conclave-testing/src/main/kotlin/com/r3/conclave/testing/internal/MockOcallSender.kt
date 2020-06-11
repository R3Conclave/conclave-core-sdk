package com.r3.conclave.testing.internal

import com.r3.conclave.common.internal.handler.LeafSender
import com.r3.conclave.common.internal.handler.HandlerConnected
import java.nio.ByteBuffer

class MockOcallSender<CONNECTION>(val handlerConnected: HandlerConnected<CONNECTION>): LeafSender() {
    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        handlerConnected.onReceive(serializedBuffer)
    }
}
