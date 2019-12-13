package com.r3.sgx.testing

import com.r3.sgx.core.common.BytesHandler
import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.RootEnclave
import java.nio.ByteBuffer

abstract class BytesEnclave : RootEnclave() {
    abstract fun onReceive(api: EnclaveApi, connection: BytesHandler.Connection, bytes: ByteBuffer)

    final override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
        mux.addDownstream(object : BytesHandler() {
            override fun onReceive(connection: BytesHandler.Connection, input: ByteBuffer) {
                this@BytesEnclave.onReceive(api, connection, input)
            }
        })
    }
}