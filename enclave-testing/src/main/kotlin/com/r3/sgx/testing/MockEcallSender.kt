package com.r3.sgx.testing

import com.r3.sgx.core.common.LeafSender
import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.HandlerConnected
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.host.EnclaveHandle
import java.nio.ByteBuffer

class MockEcallSender<CONNECTION>(
        hostHandler: Handler<CONNECTION>,
        val enclave: Enclave
) : EnclaveHandle<CONNECTION>, LeafSender() {
    override val connection: CONNECTION = hostHandler.connect(this)
    // This needs to be lazy to allow EnclaveHostMockTest to work
    private val enclaveHandler by lazy {
        enclave.initialize(
                MockEnclaveApi(enclave),
                MockOcallSender(HandlerConnected(hostHandler, connection))
        )
    }

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        enclaveHandler?.onReceive(serializedBuffer)
    }

    override fun destroy() {
    }
}
