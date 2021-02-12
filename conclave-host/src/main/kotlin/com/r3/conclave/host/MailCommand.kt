package com.r3.conclave.host

/**
 * Represents a mail command from the enclave to the host for it carry out some action on its behalf.
 *
 * These commands are delivered grouped in a list in a callback to [EnclaveHost.start]. The host gathers all the
 * commands it receives within the context of a [EnclaveHost.deliverMail] or [EnclaveHost.callEnclave] call and delivers
 * them in one go in order to the callback on the same thread. This allows the host to provide transactionality when
 * processing mail. For example, the receipt of mail from clients can occur atomically within the same database transaction
 * as the delivery of any mail replies from the enclave.
 */
sealed class MailCommand {
    /**
     * A [MailCommand] which is emitted when the enclave wants to send an encrypted message over the network
     * to a client (via `Enclave.postMail`). The host should examine the public key and/or the
     * [routingHint] parameter to decide where the enclave wants it to be sent.
     *
     * The routing hint may be "self". In that case you are expected to send the mail
     * back to the enclave when the enclave is restarted.
     *
     * You don't have to perform the actual send synchronously if that's inappropriate
     * for your app. However, the mail must be recorded for delivery synchronously, so
     * no messages can be lost in case of crash failure.
     */
    class PostMail(val encryptedBytes: ByteArray, val routingHint: String?) : MailCommand() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PostMail) return false
            return this.encryptedBytes.contentEquals(other.encryptedBytes) && this.routingHint == other.routingHint
        }

        override fun hashCode(): Int = 31 * encryptedBytes.contentHashCode() + (routingHint?.hashCode() ?: 0)
    }

    /**
     * A [MailCommand] which is emitted when the enclave wants to mark a given piece of mail as
     * acknowledged (via `Enclave.acknowledgeMail`), so it can be deleted and should not be re-delivered.
     *
     * You should perform the acknowledgement synchronously and atomically with any posts,
     * as this is required for clients to observe transactional behaviour.
     *
     * @property mailID The user-defined ID passed alongside the mail in [EnclaveHost.deliverMail].
     */
    class AcknowledgeMail(val mailID: Long) : MailCommand() {
        override fun equals(other: Any?): Boolean {
            return this === other || other is AcknowledgeMail && this.mailID == other.mailID
        }

        override fun hashCode(): Int = mailID.hashCode()
    }
}
