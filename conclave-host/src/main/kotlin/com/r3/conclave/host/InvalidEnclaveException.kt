package com.r3.conclave.host

/**
 * Exception that is thrown by the [EnclaveHost] when it's unable to start the enclave.
 */
class InvalidEnclaveException(message: String, cause: Throwable?) : Exception(message, cause)
