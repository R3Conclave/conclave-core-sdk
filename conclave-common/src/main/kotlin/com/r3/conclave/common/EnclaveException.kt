package com.r3.conclave.common

/**
 * Represents an exception that was thrown by the enclave. EnclaveHost.deliverMail and EnclaveHost.callEnclave will wrap
 * any exception coming from the enclave in a EnclaveException. If EnclaveClient is used then this exception will propagate
 * down to the client as well.
 * To prevent accidental leakage of enclave secrets, the original exception is not wrapped if the enclave is running in
 * release mode. Instead, a generic EnclaveException is thrown.
 */
open class EnclaveException(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}
