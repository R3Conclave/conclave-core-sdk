package com.r3.conclave.common

// TODO This needs to replace EnclaveLoadMode but that probably means merging api-core-host into conclave-host (which
//      needs to happen anyway).
/**
 * The mode that an enclave is running in, whether it's for safe for production, intended for debugging or uses simulated
 * hardware.
 */
enum class EnclaveMode {
    /**
     * The enclave requires a real secure hardware to run. Only this mode gives the full security guarantees of Conclave.
     */
    RELEASE,
    /**
     * The enclave requires a real secure hardware to run, however there is **backdoor** that's open that allows the host
     * to inspect the enclave's memory. This is for debugging purposes. The enclave is NOT secure in this mode.
     */
    DEBUG,
    /**
     * The enclave does not rely on a real secure hardware but rather runs off a simulation of one in software. There is
     * absolutely NO security in this mode. This mode is ONLY provided to facilitate development on developer machines
     * that may not have the necessary hardware.
     */
    SIMULATION
}
