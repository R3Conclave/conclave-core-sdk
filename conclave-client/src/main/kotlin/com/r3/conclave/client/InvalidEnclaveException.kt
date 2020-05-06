package com.r3.conclave.client

/**
 * Exception that is thrown by the [EnclaveConstraint] if an enclave violates its constraints.
 */
class InvalidEnclaveException(message: String, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
}
