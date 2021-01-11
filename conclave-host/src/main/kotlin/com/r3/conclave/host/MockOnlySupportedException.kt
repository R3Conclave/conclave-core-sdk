package com.r3.conclave.host

/**
 * Exception that is thrown by the [EnclaveHost] if only mocked enclaves are supported.
 * This exception could be thrown for a number of reasons including:
 *
 * 1. The OS is not Linux.
 * 2. The CPU doesn't support enclaves even in SIMULATION mode.
 *
 * The exception message will contain detailed information on the cause for the failure.
 */
class MockOnlySupportedException(message: String, cause: Throwable?) : EnclaveLoadException(message, cause) {
    constructor(message: String) : this(message, null)
}
