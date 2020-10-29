package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode

/**
 * A handle to an enclave instance.
 */
interface EnclaveHandle<CONNECTION> {
    /**
     * The mode in which the enclave is running in.
     */
    val enclaveMode: EnclaveMode

    /**
     * The base connection to the enclave.
     */
    val connection: CONNECTION

    /** The name of the Enclave subclass inside the sub-JVM. */
    val enclaveClassName: String

    /**
     * Destroy the enclave.
     *
     * Do not call this whilst there are non-terminated enclave threads.
     */
    fun destroy()
}