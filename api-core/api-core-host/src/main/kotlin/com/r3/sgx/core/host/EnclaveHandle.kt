package com.r3.sgx.core.host

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
     */
    @Deprecated(
            message = "Calling destroy() is not recommended as it's unreliable in the presence of other non-terminated" +
                    "enclave threads, possibly raising SIGSEGV and SIGILL signals. Instead the recommended cleanup route" +
                    "is full host process termination. We may revisit support for this functionality in the future.",
            level = DeprecationLevel.WARNING
    )
    fun destroy()
}