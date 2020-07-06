package com.r3.conclave.host.kotlin

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.host.EnclaveHost
import java.io.DataOutputStream

/**
 * Passes the given byte array to the enclave. The format of the byte
 * arrays are up to you but will typically use some sort of serialization
 * mechanism, alternatively, [DataOutputStream] is a convenient way to lay out
 * pieces of data in a fixed order.
 *
 * For this method to work the enclave class must implement [EnclaveCall]. The return
 * value of [EnclaveCall.invoke] (which can be null) is returned here.
 *
 * The enclave does not have the option of using `Enclave.callUntrustedHost` for
 * sending bytes back to the host. Use the overlaod which takes in a [EnclaveCall]
 * callback instead.
 *
 * @param bytes Bytes to send to the enclave.
 *
 * @return The return value of the enclave's [EnclaveCall.invoke].
 *
 * @throws IllegalArgumentException If the enclave does not implement [EnclaveCall]
 * or if the host has not been started.
 */
fun EnclaveHost.callEnclave(bytes: ByteArray, callback: (ByteArray) -> ByteArray?): ByteArray? {
    return callEnclave(bytes, EnclaveCall(callback))
}
