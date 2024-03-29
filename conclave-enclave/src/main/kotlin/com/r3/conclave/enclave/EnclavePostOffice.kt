package com.r3.conclave.enclave

import com.r3.conclave.mail.*
import com.r3.conclave.mail.internal.postoffice.AbstractPostOffice
import java.security.PrivateKey
import java.security.PublicKey

/**
 * A special post office which is tailored for use inside the enclave. A cached instance can be retrieved using
 * one of the [Enclave.postOffice] methods. The sender's private key is automatically set to the enclave's private
 * encryption key.
 *
 * Unlike general [PostOffice], [EnclavePostOffice] does not have a decrypt method as Mail is automatically
 * decrypted and passed to the enclave in [Enclave.receiveMail].
 *
 * [EnclavePostOffice] instances are not thread-safe and external synchronization is required if they are accessed from
 * multiple threads. However, since most mail are ordered by their sequence numbers, care should be taken to make sure
 * they are created in their intended order.
 *
 * @see [PostOffice]
 * @see [Enclave.postOffice]
 */
abstract class EnclavePostOffice(
    /**
     * The public key of the recipient to which mail will be encrypted to.
     */
    final override val destinationPublicKey: PublicKey,
    /**
     * The topic mail created by this post office will have.
     *
     * @see [EnclaveMailHeader.topic]
     */
    final override val topic: String
) : AbstractPostOffice() {
    init {
        // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
        require(destinationPublicKey is Curve25519PublicKey) {
            "At this time only Conclave originated Curve25519 public keys may be used."
        }
        checkTopic(topic)
    }

    /**
     * Returns the sender's private key used to authenticate mail and create the [EnclaveMail.authenticatedSender] field
     * for the recipient. By default, this is the enclave's random session key. However, the sender's private key is a
     * KDS key if the original Mail is encrypted using KDS.
     *
     * Experienced users can use the private key for advanced purposes. For example, you can hash the private key to
     * derive a unique ID that can’t be forged. To retain the security guarantees of the enclave, it is critical the
     * private key is not leaked out.
     *
     * @see [EnclaveMail.authenticatedSender]
     */
    public abstract override val senderPrivateKey: PrivateKey

    /**
     * Returns the [MinSizePolicy] used to apply the minimum size for each encrypted mail. If none is specified then
     * [MinSizePolicy.movingAverage] is used.
     */
    final override var minSizePolicy: MinSizePolicy
        get() = super.minSizePolicy
        set(value) {
            super.minSizePolicy = value
        }

    /**
     * Returns the sequence number that will be assigned to the next mail.
     */
    abstract val nextSequenceNumber: Long

    /**
     * Uses [destinationPublicKey] to encrypt mail with the given body. Only the corresponding private key will be able to
     * decrypt the mail. The returned ciphertext will include [topic], incremented sequence number (see [nextSequenceNumber])
     * in the clear but authenticated (for the recipient only) as coming from the holder of the sender private key.
     *
     * The recipient needs to call [PostOffice.decryptMail] on a post office with the private key of [destinationPublicKey]
     * to decrypt the bytes.
     *
     * The encoded bytes contains the [body], header and the handshake bytes that set up the shared session key.
     * A mail may not be larger than the 2 gigabyte limit of a Java byte array. The format is not defined here and
     * subject to change.
     *
     * @return the encrypted mail bytes.
     *
     * @see EnclaveMailHeader
     */
    fun encryptMail(body: ByteArray): ByteArray = encryptMail(body, null)

    /**
     * Uses [destinationPublicKey] to encrypt mail with the given body. Only the coresponding private key will be able to
     * decrypt the mail. The returned ciphertext will include [topic], incremented sequence number (see [nextSequenceNumber])
     * and [envelope] in the clear but authenticated (for the recipient only) as coming from the holder of the sender
     * private key.
     *
     * The recipient needs to call [PostOffice.decryptMail] on a post office with the private key of [destinationPublicKey]
     * to decrypt the bytes.
     *
     * The encoded bytes contains the [body], the [envelope], header and the handshake bytes that set up the shared session key.
     * A mail may not be larger than the 2 gigabyte limit of a Java byte array. The format is not defined here and
     * subject to change.
     *
     * @return the encrypted mail bytes.
     *
     * @see EnclaveMailHeader
     */
    fun encryptMail(body: ByteArray, envelope: ByteArray?): ByteArray = encryptMail(body, envelope, privateHeader)

    protected abstract val privateHeader: ByteArray?
}
