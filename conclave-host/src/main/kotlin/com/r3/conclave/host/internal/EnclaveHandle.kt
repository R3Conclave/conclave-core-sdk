package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode

/**
 * A handle to an enclave instance.
 */
interface EnclaveHandle {
    /**
     * The mode in which the enclave is running in.
     */
    val enclaveMode: EnclaveMode

    /**
     * Object for initiating enclave calls from.
     */
    val enclaveInterface: HostEnclaveInterface

    /** The name of the Enclave subclass inside the sub-JVM. */
    val enclaveClassName: String

    /**
     * For Mock mode, returns the instance of the enclave.
     * For Release, Simulation and Debug modes, throws IllegalStateException.
     */
    val mockEnclave: Any

    /**
     * Initialise the enclave.
     */
    fun initialise()

    /**
     * Destroy the enclave.
     *
     * Do not call this whilst there are non-terminated enclave threads.
     */
    fun destroy()
}