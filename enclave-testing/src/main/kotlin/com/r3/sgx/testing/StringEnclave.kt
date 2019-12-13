package com.r3.sgx.testing

import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.RootEnclave

abstract class StringEnclave : RootEnclave() {
    abstract fun onReceive(api: EnclaveApi, sender: StringSender, string: String)

    final override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
        mux.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                this@StringEnclave.onReceive(api, sender, string)
            }
        })
    }
}
