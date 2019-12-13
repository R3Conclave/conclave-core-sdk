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
    override val connection = hostHandler.connect(this)
    val ocallSender = MockOcallSender(HandlerConnected(hostHandler, connection))
    private val api = MockEnclaveApi(enclave)
    private val enclaveHandler = enclave.initialize(api, ocallSender)

    override fun sendSerialized(serializedBuffer: ByteBuffer) {
        enclaveHandler?.onReceive(serializedBuffer)
    }

    override fun destroy() {
    }
}
