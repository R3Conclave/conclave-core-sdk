package com.r3.sgx.testing

import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.RootEnclave

/**
 * An enclave that simply echoes ecalls
 */
class EchoEnclave : RootEnclave() {
    override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
        mux.addDownstream(EchoHandler())
    }
}
