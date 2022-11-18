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
     * This is to establish which entity triggers the quoting: HOST or ENCLAVE.
     * In Graal/Native Image it is responsibility of the host to use sgx_qe_get_quote and
     * pass the result of it to the enclave.
     * In Gramine it is responsibility of the enclave (and Gramine internals) to get the quote through AESM service.
     * Note: Mock mode is following Graal/Native Image approach: the host does a call to the enclave to get the quote.
     */
    val quotingManager: QuotingManager

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
