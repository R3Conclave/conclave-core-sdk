package com.r3.conclave.host

/**
 * Exception that is thrown by the [EnclaveHost] when an enclave could not be loaded or started. 
 * This exception could be thrown for a number of reasons including:
 * 
 * 1. The caller is trying to load a non-simulation enclave but the platform does not support 
 * hardware enclaves.
 * 2. The user may need to enable enclave support in the BIOS or by running the application 
 * as root
 * 3. The enclave may not be appropriately signed.
 * 4. The platform software for supporting enclaves may not be installed.
 *
 * The exception message will contain detailed information on the cause for the failure.
 */
class EnclaveLoadException(message: String, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
}
