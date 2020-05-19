package com.r3.conclave.core.enclave

import com.r3.conclave.core.common.HandlerConnected
import com.r3.conclave.core.common.Sender

/**
 * The main interface of enclaves. All Java enclaves must implement this.
 */
interface Enclave {
    /**
     * @param api the enclave API.
     * @param upstream the root upstream, making raw OCALLS.
     * @return the connected Handler if initialization was successful, null otherwise.
     */
    fun initialize(api: EnclaveApi, upstream: Sender): HandlerConnected<*>?
}
