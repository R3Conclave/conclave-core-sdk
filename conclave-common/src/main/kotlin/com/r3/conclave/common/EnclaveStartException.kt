package com.r3.conclave.common

/**
 * Exception that is thrown when the enclave is unable to start.
 *
 * EnclaveStartException has privileged status and propagates as-is if it's thrown in the enclave, even in release
 * mode. This is not the case for other exceptions which are blocked in release mode to prevent accidental leakage of
 * secrets. This privileged status is to aid troubleshooting if a production enclave is unable to start.
 */
class EnclaveStartException(message: String?, cause: Throwable?) : EnclaveException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}
