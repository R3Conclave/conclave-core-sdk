package com.r3.conclave.enclave.kotlin

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.common.enclave.EnclaveCall

/**
 * Sends the given bytes to the registered [EnclaveCall] implementation provided to [EnclaveHost.callEnclave].
 * If the host responds by doing another call back in to the enclave rather than immediately returning
 * from the [EnclaveCall], that call will be routed to [callback]. In this way a form of virtual stack can
 * be built up between host and enclave as they call back and forth.
 *
 * @return The bytes returned from the host's [EnclaveCall].
 */
fun Enclave.callUntrustedHost(bytes: ByteArray, callback: (ByteArray) -> ByteArray?): ByteArray? {
    return callUntrustedHost(bytes, EnclaveCall { callback(it) })
}
