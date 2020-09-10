package com.r3.conclave.mail

import com.r3.conclave.mail.internal.EnclaveMailHeaderImpl
import com.r3.conclave.mail.internal.MailDecryptingStream
import com.r3.conclave.mail.internal.MailEncryptingStream
import com.r3.conclave.mail.internal.noise.protocol.DHState
import com.r3.conclave.mail.internal.noise.protocol.Noise
import java.io.ByteArrayOutputStream
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
// TODO: Research how best to buffer messages to disk for unacknowledged mails (log with confirms/deletes)
// TODO: Implement support for very large mails (seekable access).
// TODO: Implement size padding properly.

/**
 * A 64 bit identifier for a delivered message (is unsigned, i.e. may be negative). Mail IDs should be treated as
 * opaques, they are possibly random.
 */
typealias EnclaveMailId = Long

/**
 * Access to the headers on a mail.
 *
 * The data isn't signed, it's authenticated data provided to the AES/GCM algorithm. This means
 * it's tamperproof only to the holder of the receiving private key, not anyone else.
 *
 * Therefore the data may be authenticated or unauthenticated, depending on how you obtained this
 * object. If this is a [MutableMail] then you're the one setting the data so the question of
 * authenticity is irrelevant. If the mail was delivered into the enclave then the headers were
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
     * The topic is used to organise different streams of mail, like a subject line in
     * email threads does. The topic is not parsed by Conclave and should be unique
     * across time to avoid replay attacks: a good value might thus contain a random
     * UUID. You can also think of this as a connection or session ID. Topics may be
     * logged and used by your software to route, load balance, or otherwise split
     * mail streams in useful ways.
     *
     * There must be a topic and it cannot be empty, but if you don't need this
     * feature then the pre-filled value of "default" is fine. It has a maximum length
     * of 256 characters. It should contain only the following characters:
     * a-zA-Z0-9 and - but no others. This allows the topic to be logged, used
     * as a file name, fed directly into MQ engines and so on.
     */
    val topic: String

    /**
     * If present, contains a string the host may use to route replies. This is
     * analogous to the From field in email and just like email, it's
     * unauthenticated: senders can put whatever routing hint they want here. However
     * if mail is encrypted to the authenticated sender, then misdirected replies
     * won't be readable.
     */
    val from: String?

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
 * The decrypted version of an encrypted message sent from a client to an enclave.
 *
 * Implementations may decode raw bytes on demand, or may represent mail that isn't
 * encoded yet.
 */
interface EnclaveMail : EnclaveMailHeader {
    /**
     * The public key of the sender or null if none provided. This field is authenticated,
     * so only the holder of the corresponding private key can get that public key into
     * this field. Can be used to encrypt a reply back to them, used for user authentication and
     * so on.
     */
    val authenticatedSender: PublicKey?

    /**
     * Whatever data was encrypted into the mail by the sender.
     */
    val bodyAsBytes: ByteArray
}

/**
 * Mail that's in the process of being built. All mail must have a body and
 * a destination public key so the body can be encrypted. You may also specify
 * your own [privateKey], the public part of which will be received by the recipient
 * in an encrypted and authenticated manner i.e. others cannot impersonate your key,
 * nor can the enclave host learn what your public key is, only the enclave can.
 *
 * Mail may additionally be given a [topic] and a [sequenceNumber], which aids in
 * sorting independent threads of messages. You may provide arbitrary bytes as an
 * [envelope], which is unencrypted but authenticated, so the network and remote
 * untrusted host software can't tamper with it but can use it for routing,
 * prioritisation and so on. If no topic is provided then "default" is used. The
 * topic may not be the empty string.
 *
 * Finally, a [from] header may be optionally provided to assist the remote
 * host in routing replies back to you.
 *
 * When the fields are set correctly call [encrypt] to get back the encrypted byte array.
 *
 * NOTE: When creating mail destined for an enclave, `EnclaveInstanceInfo.createMail` must be used and not the
 * constructors.
 */
class MutableMail(
        override var bodyAsBytes: ByteArray,
        private val destinationKey: PublicKey,
        var privateKey: PrivateKey? = null
) : EnclaveMail {

    constructor(body: ByteArray, destinationKey: PublicKey) : this(body, destinationKey, null)

    init {
        // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
        require(destinationKey is Curve25519PublicKey) {
            "At this time only Conclave originated Curve25519 public keys may be used."
        }
    }

    // Internal header field for storing any key derivation data. Currently this is only used by the enclave.
    internal var keyDerivation: ByteArray? = null

    override var sequenceNumber: Long = 0

    /**
     * The [minSize] parameter can be set to be larger than any message you reasonably
     * expect to send. The encrypted bytes will be padded to be at least this size,
     * closing a message size side channel that could give away hints about the content.
     */
    var minSize: Int = 0

    /**
     * Increments the [sequenceNumber] field by one. Not thread safe.
     */
    fun incrementSequenceNumber() {
        sequenceNumber++
    }

    override var topic: String = "default"
        set(value) {
            require(value.isNotBlank())
            require(value.length < 256) { "Topic length must be < 256 characters, is ${topic.length}" }
            for ((index, char) in value.withIndex()) {
                if (!(char.isLetterOrDigit() || char == '-'))
                    throw IllegalArgumentException("Character $index of the topic is not a character, digit or -")
            }
            field = value
        }
    override var from: String? = null
    override var envelope: ByteArray? = null
    override val authenticatedSender: PublicKey? get() = privateKey?.let { privateToPublic(it) }

    // Convert to the corresponding public key.
    private fun privateToPublic(privateKey: PrivateKey): PublicKey {
        val dh: DHState = Noise.createDH("25519")
        dh.setPrivateKey(privateKey.encoded, 0)
        return Curve25519PublicKey(dh.publicKey)
    }

    /**
     * Uses the public key provided in the constructor to encrypt the mail. The returned ciphertext will include
     * [sequenceNumber], [topic], [from] and [envelope] in the clear but authenticated (for the recipient only)
     * as coming from the holder of the [privateKey] (if one was specified).
     *
     * @return a ciphertext that can be fed to [Mail.decrypt] to obtain the original mail.
     */
    fun encrypt(): ByteArray {
        val header: ByteArray = EnclaveMailHeaderImpl(sequenceNumber, topic, from, envelope, keyDerivation).encoded
        val output = ByteArrayOutputStream()
        val stream = MailEncryptingStream.wrap(output, destinationKey, header, privateKey)
        stream.write(bodyAsBytes)
        stream.close()
        return output.toByteArray()
    }
}

/**
 * Access to mail decryption and parsing. To create and encrypt mail, use [MutableMail] instead.
 */
object Mail {
    /**
     * Decodes and decrypts the mail with the given private key. When inside an enclave, decryption is done for you.
     * When outside an enclave, it's better to use `EnclaveInstanceInfo.decryptMail` as that will verify the sender
     * using the enclave's key for you.
     *
     * @param withKey the Curve25519 private key to which the mail was encrypted.
     * @param encryptedEnclaveMail The encoded bytes containing the body, the envelope, the
     * handshake bytes that set up the shared session key and so on. A mail may not
     * be larger than the 2 gigabyte limit of a Java byte array. The format is not defined
     * here and subject to change.
     *
     * @throws IllegalArgumentException if the mail can't be decrypted with this key.
     * @throws IOException if the mail is malformed or corrupted in some way.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun decrypt(encryptedEnclaveMail: ByteArray, withKey: PrivateKey): EnclaveMail {
        // This is a runtime check so we can switch to JDK11+ types later without breaking our own API.
        require(withKey is Curve25519PrivateKey) {
            "At this time only Conclave originated Curve25519 private keys may be used."
        }
        return MailDecryptingStream(encryptedEnclaveMail.inputStream()).decryptMail { withKey }
    }

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
        return MailDecryptingStream(encryptedEnclaveMail.inputStream()).header
    }
}

// TODO: This exception doesn't propagate across enclave boundaries. Figure out exception handling in more detail.

/**
 * Thrown if mail is delivered in a different order to the sequence numbers in the header require. This exception
 * indicates a bug in the host (or a maliciously tampered with host).
 */
class InvalidSequenceException(val attemptedSequenceNumber: Long, val highestSequenceNumber: Long) :
        RuntimeException("Mail delivered out of order or replayed. Highest sequence number seen is " +
                "$highestSequenceNumber, attempted delivery of $attemptedSequenceNumber")