package com.r3.conclave.core.enclave.internal

import com.r3.conclave.core.common.HandlerConnected
import com.r3.conclave.core.common.Sender
import com.r3.conclave.core.enclave.EnclaveApi

/**
 * A shim layer to enable the Conclave enclave API to hook into the existing host-enclave JNI pathway.
 */
interface ConclaveLoader {
    fun loadEnclave(enclaveClass: Class<*>, api: EnclaveApi, upstream: Sender): HandlerConnected<*>?
}
