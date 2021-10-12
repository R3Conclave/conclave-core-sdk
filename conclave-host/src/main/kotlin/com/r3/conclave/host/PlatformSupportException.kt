package com.r3.conclave.host

/**
 * Exception that is thrown by the [EnclaveHost] when an operation fails due to lack of platform support.
 * This exception could be thrown for a number of reasons including:
 *
 * 1. The OS is not Linux.
 * 2. The CPU doesn't support enclaves even in SIMULATION mode.
 *
 * The exception message will contain detailed information on the cause for the failure.
 */
class PlatformSupportException(message: String, cause: Throwable?) : EnclaveLoadException(message, cause) {
    constructor(message: String) : this(message, null)
}
