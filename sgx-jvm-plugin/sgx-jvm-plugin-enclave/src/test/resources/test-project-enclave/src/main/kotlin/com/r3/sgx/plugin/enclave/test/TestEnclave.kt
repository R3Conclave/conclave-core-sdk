package com.r3.sgx.plugin.enclave.test

import com.r3.sgx.core.common.HandlerConnected
import com.r3.sgx.core.common.Sender
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.enclave.EnclaveApi

class TestEnclave : Enclave {
    override fun initialize(api: EnclaveApi, upstream: Sender): HandlerConnected<*>? {
        TODO("not implemented")
    }
}
