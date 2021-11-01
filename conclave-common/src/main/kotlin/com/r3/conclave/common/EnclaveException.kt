package com.r3.conclave.common

/**
 * Exception that's thrown by the client if the enclave threw an exception.
 */
// TODO This also needs to be thrown by the host
class EnclaveException(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}
