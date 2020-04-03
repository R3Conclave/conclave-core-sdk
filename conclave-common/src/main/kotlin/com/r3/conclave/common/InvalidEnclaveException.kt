package com.r3.conclave.common

/**
 * Exception that is thrown by the [EnclaveHost] when it's unable to start the enclave.
 */
class InvalidEnclaveException(message: String, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String) : this(message, null)
}
