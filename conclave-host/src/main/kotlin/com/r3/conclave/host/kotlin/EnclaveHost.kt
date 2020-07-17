package com.r3.conclave.host.kotlin

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveHost.MailCallbacks
import com.r3.conclave.mail.EnclaveMailId
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


/**
 * Delivers the given encrypted mail bytes to the enclave. If the enclave throws
 * an exception it will be rethrown.
 * It's up to the caller to decide what to do with mails that don't seem to be
 * handled properly: discarding it and logging an error is a simple option, or
 * alternatively queuing it to disk in anticipation of a bug fix or upgrade
 * is also workable.
 *
 * There is likely to be a callback on the same thread to the
 * [MailCallbacks.postMail] function, requesting mail to be sent back in response
 * and/or acknowledgement. However, it's also possible the enclave will hold
 * the mail without requesting any action.
 *
 * When an enclave is started, you must redeliver, in order, any unacknowledged
 * mail so the enclave can rebuild its internal state.
 *
 * @param id an identifier that may be passed to [MailCallbacks.acknowledgeMail]
 * @param mail the encrypted mail received from a remote client.
 * @param callback If the enclave calls `Enclave.callUntrustedHost` then the
 * bytes will be passed to this object for consumption and generation of the
 * response.
 * @throws IllegalStateException if the enclave has not been started.
 */
fun EnclaveHost.deliverMail(id: EnclaveMailId, mail: ByteArray, callback: (ByteArray) -> ByteArray?) {
    return deliverMail(id, mail, EnclaveCall(callback))
}