package com.r3.conclave.core.host

/**
 * A handle to an enclave instance.
 */
interface EnclaveHandle<CONNECTION> {
    /**
     * The base connection to the enclave.
     */
    val connection: CONNECTION

    /**
     * Destroy the enclave.
     *
     * Do not call this whilst there are non-terminated enclave threads.
     */
    fun destroy()
}