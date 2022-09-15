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

    /**
     * Object for initiating enclave calls from.
     */
    val callInterface: EnclaveCallInterface

    /** The name of the Enclave subclass inside the sub-JVM. */
    val enclaveClassName: String

    /**
     * For Mock mode, returns the instance of the enclave.
     * For Release, Simulation and Debug modes, throws IllegalStateException.
     */
    val mockEnclave: Any

    /**
     * Destroy the enclave.
     *
     * Do not call this whilst there are non-terminated enclave threads.
     */
    fun destroy()
}