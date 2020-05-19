package com.r3.conclave.testing

import com.r3.conclave.core.common.SimpleMuxingHandler
import com.r3.conclave.core.enclave.EnclaveApi
import com.r3.conclave.core.enclave.RootEnclave

/**
 * An enclave that simply echoes ecalls
 */
class EchoEnclave : RootEnclave() {
    override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
        mux.addDownstream(EchoHandler())
    }
}
