package com.r3.conclave.mail

import com.r3.conclave.mail.internal.MailDecryptingStream
import com.r3.conclave.mail.internal.postoffice.AbstractPostOffice
import com.r3.conclave.mail.internal.privateCurve25519KeyToPublic
import com.r3.conclave.utilities.internal.EnclaveContext
import java.io.IOException
import java.security.PrivateKey
import java.security.PublicKey

// TODO: Add sample demo code with a simple HTTP binding, document.
// TODO: Key types probably need to be public or properly wired to JCA - cannot assume they are only retrieved from
//       an EnclaveInstanceInfo. Could be e.g. sent by a client?
// TODO: Rethink how re-delivery and persistence is integrated, how do enclaves send responses to the host when a mail
//       is delivered, if re-delivery could disconnect any callbacks? Maybe just remove the ability to send responses.
// TODO: Improve the client API design doc section to actually describe the EnclaveClient interface/class.
//       Figure out the request-with-response, request-with-no-response API.
// TODO: Implement support for very large mails (seekable access).

/**
 * Access to the headers on a mail.
 *
 * The data isn't signed, it's authenticated data provided to the AES/GCM algorithm. This means
 * it's tamperproof only to the holder of the receiving private key, not anyone else.
 *
 * Therefore the data may be authenticated or unauthenticated, depending on how you obtained this
 * object. If the mail was delivered into the enclave then the headers were
 * checked, and thus can be treated as authentic (not tampered with by the host).
 *
 * From the host you can read this data using [Mail.getUnauthenticatedHeader] but you have no
 * guarantee it wasn't tampered with in transmission from the client, unless the path between
 * the client and host is protected by another layer of encryption/authentication, such as TLS.
 * For this reason we recommend that mail is always sent over an encrypted channel between client
 * and host, even though mail is itself also encrypted i.e. there are two layers, one for
 * protecting client<->host and one for protecting client<->enclave. TLS isn't used directly into
 * the enclave for reasons discussed further in the documentation.
 */
interface EnclaveMailHeader {
    /**
     * The sequence number should be unique and incrementing within the scope of a
     * single topic and sender. Enclaves will check that delivered mail has
     * incrementing sequence numbers to ensure the untrusted host isn't dropping
     * or re-ordering messages, which could otherwise be exploited in an attack.
     *
     * Note that the sequence number is not used as an initialization vector for
     * encryption, so repeated use of the same number does not risk leaking bytes,
     * only confusing the enclave and/or host.
     */
    val sequenceNumber: Long

    /**
     * The topic can be used to distinguish different streams of mail from the same client.
     * It can be thought of as equivalent to an email subject. Topics are scoped per-sender.
     * The client can send multiple streams of related mail by using a different topic for
     * each stream, and it can do this concurrently. The topic is not parsed by Conclave
     * and, to avoid replay attacks, should never be reused for an unrelated set of mails
     * in the future. A good value might thus contain a random UUID. Topics may be logged
     * and used by your software to route or split mail streams in useful ways.
     *
     * There must be a topic and it cannot be empty, but if you don't need this
     * feature then the pre-filled value of "default" is fine. It has a maximum length
     * of 256 characters. It should contain only the following characters:
     * a-zA-Z0-9 and - but no others. This allows the topic to be logged, used
     * as a file name, fed directly into MQ engines and so on.
     */
    val topic: String

    /**
     * Extension area for apps to put whatever additional fields or data they would
     * like. Like with the other headers, the envelope is not encrypted but is
     * authenticated, so intermediaries and the untrusted host cannot tamper with
     * it. This is useful to assist with protocols where the untrusted host needs
     * to collaborate with the enclave on each message.
     */
    val envelope: ByteArray?
}

/**
 * The decrypted version of an encrypted message sent from a client to an enclave and vice-versa.
 *
 * Implementations may decode raw bytes on demand, or may represent mail that isn't
 * encoded yet.
 */
interface EnclaveMail : EnclaveMailHeader {
    /**
     * The encrypted public key of the sender. This field is authenticated,
     * so only the holder of the corresponding private key can get that public key into
     * this field. Can be used to encrypt a reply back to them, used for user authentication and
     * so on.
     */
    val authenticatedSender: PublicKey

    /**
     * Whatever data was encrypted into the mail by the sender.
     */
    val bodyAsBytes: ByteArray
}

/**
 * A post office is an object for creating a stream of related mail encrypted to a [destinationPublicKey].
 *
 * Related mail form an ordered list on the same [topic]. This ordering is defined by the sequence number field in each
 * mail header (see [EnclaveMailHeader.getSequenceNumber](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.mail/-enclave-mail-header/get-sequence-number.html)
 * and it's important the ordering is preserved for the receiving enclave.
 *
 * A post office also requires a [senderPrivateKey], which is used to authenticate each mail and is received by the
 * recipient as an authenticated public key (see [EnclaveMail.getAuthenticatedSender]). This can be used by the
 * recipient for user authentication but is also required if they want to reply back.
 *
 * The sender key can either be a short-term eptherimal key which is used only once and then discarded (e.g. when the client
 * process exits) or it can be a long-term identity key. If the later then it's important to keep track of the current
 * sequence number (using [nextSequenceNumber]) and persit it so that in the event of a client restart the sequence number
 * can be restored (using [setNextSequenceNumber]). Otherwise the it will reset to zero and since the enclave has already
 * seen mail with the same sender key it will reject it.
 *
 * Starting from zero, the post office applies an increasing sequence number to each mail it creates. The sequence number,
 * along with the topic and optional envelope (which can be provided when encrypting), are authenticted to the receiving
 * enclave. This means it can detect dropped or reordered messages and thus the ordering is preserved.
 *
 * However for this to work, the same post office instance must be used for the same sender key and topic pair. This
 * means there can only be one [PostOffice] instance per (destination, sender, topic) triple. It's up the user to make
 * sure this is the case.
 *
 * To make it make it more difficult for an adversary to guess the contents of the mail just by observing their sizes,
 * the post office pads the mail to a minimum size. By default it uses a moving average of the previous mail created.
 * This can be changed with [minSizePolicy] if that's not a sensible policy for the application.
 *
 * The recepient of mail can decrypt using [decryptMail] on a post office instance which has the same private key.
 * For a mail response this will be the same post office that created the original request. Inside an enclave nothing
 * needs to be done as mail is automatically decrypted.
 *
 * When inside an enclave instances can only be created using one of the [com.r3.conclave.enclave.Enclave.postOffice] methods, and cannot be
 * created using [create] or [com.r3.conclave.common.EnclaveInstanceInfo.createPostOffice]. This is to ensure the enclave's
 * private key is correctly applied as the sender.
 *
 * [PostOffice] instances are not thread-safe and external synchronization is required if they are accessed from
 * multiple threads. However, since most mail are ordered by their sequence numbers, care should be taken to make sure
 * they are created in their intended order.
 */
abstract class PostOffice(
    /**
     * The sender private key used to authenticate mail and create the [EnclaveMail.getAuthenticatedSender](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.mail/-enclave-mail/get-authenticated-sender.html) field.
     *
     * @see [EnclaveMail.getAuthenticatedSender](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.mail/-enclave-mail/get-authenticated-sender.html)
     */
    public final override val senderPrivateKey: PrivateKey,
    /**
     * The topic mail created by this post office will have.
     *
     * @see [EnclaveMailHeader.topic]
     */
    final override val topic: String
) : AbstractPostOffice() {
    /**
     * @suppress
     */
    companion object {
        /**
         * Create a new post office instance for encrypting mail to the given recipient. Each mail will be authenticated
         * with the given private key and will have the given topic.
         *
         * A new random sender key can be created using [Curve25519PrivateKey.random](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.mail/-curve25519-private-key/random.html).
         *
         * Do not use this for mail targeted at an enclave. Instead use [com.r3.conclave.common.EnclaveInstanceInfo.createPostOffice], or if
         * inside an enclave, [com.r3.conclave.enclave.Enclave.postOffice].
         */
        @JvmStatic
        fun create(destinationPublicKey: PublicKey, senderPrivateKey: PrivateKey, topic: String): PostOffice {
            return DefaultPostOffice(destinationPublicKey, senderPrivateKey, topic)
        }

        /**
         * Create a new post office instance for encrypting mail to the given recipient. A random sender private key will
         * be created and each mail will be authenticated with it (it can be retrieved using [senderPrivateKey]). The mail
         * topic will be "default".
         *
         * Do not use this for mail targeted at an enclave. Instead use [com.r3.conclave.common.EnclaveInstanceInfo.createPostOffice], or if
         * inside an enclave, [com.r3.conclave.enclave.Enclave.postOffice].
         */
        @JvmStatic
        fun create(destinationPublicKey: PublicKey): PostOffice {
            return create(destinationPublicKey, Curve25519PrivateKey.random(), "default")
        }
    }

    init {
        // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
        require(senderPrivateKey is Curve25519PrivateKey) {
            "At this time only Conclave originated Curve25519 private keys may be used."
        }
        checkTopic(topic)
        check(!EnclaveContext.isInsideEnclave()) {
            "Use one of the Enclave.postOffice() methods for getting a PostOffice instance when inside an enclave."
        }
    }

    private var sequenceNumber: Long = 0

    /**
     * The public key of the recipient to which mail will be encrypted to.
     */
    abstract override val destinationPublicKey: PublicKey

    /**
     * Returns the corresponding public key of [senderPrivateKey]. The recipient of mail will receive this as the
     * authenticated sender. They can use it to reply back, and we can use [decryptMail] on this post office instance
     * to decrypt their response.
     *
     * @see [EnclaveMail.authenticatedSender]
     */
    val senderPublicKey: PublicKey get() = privateCurve25519KeyToPublic(senderPrivateKey)

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
    val nextSequenceNumber: Long get() = sequenceNumber

    /**
     * Set the next sequence number to be used. This can only be called before any mail have been encrypted to ensure
     * they have increasing sequence numbers.
     *
     * You would typically only need to use this method to restore a post office if the private key is a long-term identity
     * key. The enclave expects topics to start from zero and so setting this to any other value for a new stream will
     * cause the enclave to reject the mail.
     *
     * @throws IllegalStateException If mail has already been created.
     * @throws IllegalArgumentException If the number is negative.
     */
    fun setNextSequenceNumber(sequenceNumber: Long): PostOffice {
        check(!encryptCalled) { "Cannot change the sequence number once mail has been created." }
        require(sequenceNumber >= 0) { "Sequence number cannot be negative." }
        this.sequenceNumber = sequenceNumber
        return this
    }

    override fun getAndIncrementSequenceNumber(): Long = sequenceNumber++

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
     * The encoded bytes contains the [body], the [envelope], header, the handshake bytes that set up the shared session key.
     * A mail may not be larger than the 2 gigabyte limit of a Java byte array. The format is not defined here and
     * subject to change.
     *
     * @return the encrypted mail bytes.
     *
     * @see EnclaveMailHeader
     */
    fun encryptMail(body: ByteArray, envelope: ByteArray?): ByteArray {
        return super.encryptMail(body, envelope, null)
    }

    /**
     * Decodes and decrypts the mail with [senderPrivateKey] and verifies that the authenticated sender
     * ([EnclaveMail.getAuthenticatedSender](https://docs.conclave.net/api/-conclave%20-core/com.r3.conclave.mail/-enclave-mail/get-authenticated-sender.html)
     * matches the [destinationPublicKey].
     *
     * @param encryptedEnclaveMail The encrypted mail bytes, produced by the sender's [encryptMail].
     *
     * @throws MailDecryptionException If the mail could not be decrypted, either due to key mismatch or due to
     * corrupted bytes.
     * @throws IOException If the mail is decryptable but is malformed or corrupted in some way other.
     * @throws IllegalArgumentException If the mail is not targeted to the destination public key.
     */
    @Throws(MailDecryptionException::class, IOException::class)
    fun decryptMail(encryptedEnclaveMail: ByteArray): EnclaveMail {
        return decryptMail(encryptedEnclaveMail, senderPrivateKey, destinationPublicKey)
    }

    private class DefaultPostOffice(
        override val destinationPublicKey: PublicKey,
        senderPrivateKey: PrivateKey,
        topic: String
    ) : PostOffice(senderPrivateKey, topic) {
        init {
            // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
            require(destinationPublicKey is Curve25519PublicKey) {
                "At this time only Conclave originated Curve25519 public keys may be used."
            }
        }

        override val keyDerivation: ByteArray? get() = null
    }
}

/**
 * Access to mail utilities. To encrypt mail, use [PostOffice].
 */
object Mail {
    /**
     * Returns the header data from the stream but unauthenticated. If nothing else has protected this message then
     * someone may have tampered with the headers. The reason there's no way to get an authenticated header without
     * the private key to which it was sent is due to the desire to allow sending of anonymous mails, for which the
     * host cannot learn the sender public key. Combined with other features this allows you to solve various
     * problems in which the host should not learn the identity of the clients.
     *
     * @throws IOException if an encoding problem occurs with the stream.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getUnauthenticatedHeader(encryptedEnclaveMail: ByteArray): EnclaveMailHeader {
        return MailDecryptingStream(encryptedEnclaveMail).header
    }
}
