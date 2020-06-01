package com.r3.conclave.testing

import com.r3.conclave.core.common.LeafSender
import com.r3.conclave.core.common.Handler
import com.r3.conclave.core.common.HandlerConnected
import com.r3.conclave.core.common.Sender
import com.r3.conclave.core.enclave.EnclaveApi
import com.r3.conclave.core.host.EnclaveHandle
import com.r3.conclave.enclave.Enclave
import java.nio.ByteBuffer

class MockEnclaveHandle<CONNECTION>(
        hostHandler: Handler<CONNECTION>,
        val enclave: Enclave
) : EnclaveHandle<CONNECTION>, LeafSender() {
    companion object {
        // The use of reflection is not ideal but it means we don't expose something that shouldn't be in the public API.
        private val initialiseMethod = Enclave::class.java.getDeclaredMethod("initialise", EnclaveApi::class.java, Sender::class.java).apply { isAccessible = true }
    }

    override val connection: CONNECTION = hostHandler.connect(this)
    
    private val enclaveHandler by lazy {
        val sender = MockOcallSender(HandlerConnected(hostHandler, connection))
        initialiseMethod.invoke(enclave, MockEnclaveApi(), sender) as HandlerConnected<*>
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        enclaveHandler.onReceive(serializedBuffer)
    }

    override fun destroy() {
    }
}
