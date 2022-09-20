package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.LeafSender
import com.r3.conclave.utilities.internal.EnclaveContext
import com.r3.conclave.common.internal.ThreadLocalEnclaveContext
import java.nio.ByteBuffer

class MockOcallSender<CONNECTION>(private val handlerConnected: HandlerConnected<CONNECTION>) : LeafSender() {
    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        check(EnclaveContext.isInsideEnclave())
        ThreadLocalEnclaveContext.set(false)
        try {
            handlerConnected.onReceive(serializedBuffer)
        } finally {
            ThreadLocalEnclaveContext.set(true)
        }
    }
}
