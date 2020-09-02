package com.r3.conclave.common

/**
 * A transformation of byte array to optional result byte array. EnclaveCall
 * may be implemented on `Enclave` to allow it to receive byte arrays from the
 * untrusted host and respond, but it may also be implemented by temporary
 * closures to allow a form of virtual stack to be created that threads between
 * the untrusted host and the enclave.
 */
@FunctionalInterface
fun interface EnclaveCall {
    operator fun invoke(bytes: ByteArray): ByteArray?
}
