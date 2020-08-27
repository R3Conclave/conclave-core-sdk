package com.r3.conclave.enclave.kotlin

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.common.EnclaveCall

/**
 * Sends the given bytes to the registered [EnclaveCall] implementation provided to `EnclaveHost.callEnclave`.
 * If the host responds by doing another call back in to the enclave rather than immediately returning
 * from the [EnclaveCall], that call will be routed to [callback]. In this way a form of virtual stack can
 * be built up between host and enclave as they call back and forth.
 *
 * @return The bytes returned from the host's [EnclaveCall].
 */
// TODO This extension method is no longer needed as the shading of Kotlin in the Conclave SDK somehow makes it redundant.
//      (as demonstrated by the kotlin-enclave integration test). The only code affected by removing this are the internal
//      tests. Kotlin 1.4 provides better SAM support so remove this when we upgrade to 1.4 to reduce code churn.
fun Enclave.callUntrustedHost(bytes: ByteArray, callback: (ByteArray) -> ByteArray?): ByteArray? {
    return callUntrustedHost(bytes, EnclaveCall(callback))
}
