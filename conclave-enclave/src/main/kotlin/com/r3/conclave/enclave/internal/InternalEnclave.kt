package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender

// For testing purposes
interface InternalEnclave {
    fun internalInitialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*>
}
