package com.r3.conclave.testing

import com.r3.conclave.core.common.LeafSender
import com.r3.conclave.core.common.Handler
import com.r3.conclave.core.common.HandlerConnected
import com.r3.conclave.core.enclave.Enclave
import com.r3.conclave.core.host.EnclaveHandle
import java.nio.ByteBuffer

class MockEnclaveHandle<CONNECTION>(
        hostHandler: Handler<CONNECTION>,
        val enclave: Enclave
) : EnclaveHandle<CONNECTION>, LeafSender() {
    override val connection: CONNECTION = hostHandler.connect(this)
    // This needs to be lazy to allow EnclaveHostMockTest to work
    private val enclaveHandler by lazy {
        enclave.initialize(
                MockEnclaveApi(),
                MockOcallSender(HandlerConnected(hostHandler, connection))
        )
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        enclaveHandler?.onReceive(serializedBuffer)
    }

    override fun destroy() {
    }
}
