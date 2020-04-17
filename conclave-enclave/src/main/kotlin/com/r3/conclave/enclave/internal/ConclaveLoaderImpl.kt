package com.r3.conclave.enclave.internal

import com.r3.conclave.enclave.Enclave
import com.r3.sgx.core.common.HandlerConnected
import com.r3.sgx.core.common.Sender
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.internal.ConclaveLoader

// TODO We need to load the enclave in a custom classloader that locks out internal packages of the public API.
//      This wouldn't be needed with Java modules, but the enclave environment runs in Java 8.
class ConclaveLoaderImpl : ConclaveLoader {
    override fun loadEnclave(enclaveClass: Class<*>, api: EnclaveApi, upstream: Sender): HandlerConnected<*>? {
        val enclave = enclaveClass.asSubclass(Enclave::class.java).newInstance()
        return enclave.rootEnclave.initialize(api, upstream)
    }
}
