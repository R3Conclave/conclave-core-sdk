package com.r3.conclave.enclave.internal

import com.r3.conclave.core.common.HandlerConnected
import com.r3.conclave.core.common.Sender
import com.r3.conclave.core.enclave.EnclaveApi

// For testing purposes
interface InternalEnclave {
    fun initialise(api: EnclaveApi, upstream: Sender): HandlerConnected<*>
}
