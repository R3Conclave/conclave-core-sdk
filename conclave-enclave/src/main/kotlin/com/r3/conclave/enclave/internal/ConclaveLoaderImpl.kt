package com.r3.conclave.enclave.internal

import com.r3.conclave.enclave.Enclave
import com.r3.sgx.core.common.HandlerConnected
import com.r3.sgx.core.common.Sender
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.internal.ConclaveLoader
import com.r3.sgx.core.enclave.internal.NativeEnclaveApi.ENCLAVE_CLASS_ATTRIBUTE_NAME

// TODO We need to load the enclave in a custom classloader that locks out internal packages of the public API.
//      This wouldn't be needed with Java modules, but the enclave environment runs in Java 8.
class ConclaveLoaderImpl : ConclaveLoader {
    override fun loadEnclave(enclaveClass: Class<*>, api: EnclaveApi, upstream: Sender): HandlerConnected<*>? {
        require(Enclave::class.java.isAssignableFrom(enclaveClass)) {
            "Class specified in manifest $ENCLAVE_CLASS_ATTRIBUTE_NAME does not extend ${Enclave::class.java.name}"
        }
        val enclave = enclaveClass.asSubclass(Enclave::class.java).newInstance()
        return enclave.rootEnclave.initialize(api, upstream)
    }
}
