package com.r3.conclave.mail.internal

import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.mail.internal.noise.protocol.CipherState
import com.r3.conclave.mail.internal.noise.protocol.CipherStatePair
import com.r3.conclave.mail.internal.noise.protocol.HandshakeState
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.dataStream
import com.r3.conclave.utilities.internal.readExactlyNBytes
import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.security.PrivateKey
import javax.crypto.AEADBadTagException

/**
 * A stream filter that decrypts a stream produced by [MailEncryptingStream].
 *
 * This stream filter will verify that the underlying stream doesn't prematurely terminate. Attempting to read from
 * it when the underlying stream has reached end-of-stream before the sender had finished writing will cause an
 * exception to be thrown, as otherwise a man in the middle could maliciously truncate the stream, possibly changing
 * its meaning.
 *
 * Mail streams protect you from a classic error in cryptography: checking the validity of a message only once it's
 * been entirely read but acting on it earlier. Mail streams won't yield any data until a full packet and its
 * authentication tag have been read and verified, so the stream cannot yield data produced by an adversary.
 * However when later bytes may change the meaning of earlier bytes may be affected and so you should fully consume the
 * stream (until [read] returns -1) before acting on it.
 *
 * Perhaps surprisingly, the private key to decrypt the stream is optional. By not providing one you are only able to
 * read the user provided associated data ([header]), but it will not have been authenticated. This is useful if you
 * don't have the private key but want to read the (unauthenticated) header, and secondly it allows embedding
 * material needed for deriving the key in the header. Once the key is known, calling [setPrivateKey] authenticates the
 * header (if it has been read) and enables decryption of the stream.
 *
 * Marks are not supported by this stream.
 */
class MailDecryptingStream(
    input: InputStream,
    private var privateKey: PrivateKey? = null
) : FilterInputStream(input) {
    constructor(bytes: ByteArray, privateKey: PrivateKey? = null) : this(bytes.inputStream(), privateKey)

    private var cipherState: CipherState? = null

    // Remember the exception we threw so we can throw it again if the user keeps trying to use the stream.
    private var handshakeFailure: MailDecryptionException? = null


    private lateinit var _senderPublicKey: ByteArray

    /**
     * Returns the authenticated public key of the sender. This may be useful to understand who sent you the
     * data, if you know the sender's possible public keys in advance.
     */
    val senderPublicKey: ByteArray
        get() {
            maybeHandshake()
            return _senderPublicKey
        }

    // We have a separate flag to track whether the private key has been provided or not because we want to be able to
    // clear the key as soon as it's not needed.
    private var privateKeyProvided = privateKey != null

    // The encrypted header from the sender, null until it has been received after a successful handshake or if no
    // encrypted header is present
    internal var privateHeader: ByteArray? = null
    private var privateHeaderRead: Boolean = false

    /**
     * Provide the private key needed to decrypt the stream. If the header has already been read this method will immediately
     * authenticate it.
     */
    fun setPrivateKey(privateKey: PrivateKey) {
        check(!privateKeyProvided) { "Private key has already been provided." }
        this.privateKey = privateKey
        privateKeyProvided = true
        if (_prologue != null) {
            // If the header has already been read then make sure it's valid.
            maybeHandshake()
        }
    }

    private fun readUnsignedByte(): Int = `in`.read().also { if (it == -1) error("Truncated stream") }

    private fun readUnsignedShort(): Int {
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        return (b1 shl 8) or b2
    }

    private fun readLengthPrefixBytes(): ByteArray {
        val length = readUnsignedShort()
        return `in`.readExactlyNBytes(length)
    }

    private class Prologue(val protocol: MailProtocol, val header: EnclaveMailHeaderImpl, val raw: ByteArray)

    private var _prologue: Prologue? = null

    /**
     * Access to the prologue data, without performing any authentication checks. To verify the prologue wasn't
     * tampered with you must complete the Noise handshake.
     */
    private val prologue: Prologue
        get() {
            _prologue?.let { return it }
            // The prologue is accessed on first read() (if not before) and so this will always be reading from the beginning
            // of the stream.
            //
            // See computePrologue for the format.
            val prologueBytes = try {
                readLengthPrefixBytes()
            } catch (e: EOFException) {
                error("Premature end of stream whilst reading the prologue", e)
            }
            val prologueStream = prologueBytes.dataStream()
            val prologue = try {
                val protocolId = prologueStream.readUnsignedByte()
                if (protocolId >= protocolValues.size) {
                    error("Invalid protocol ID $protocolId")
                }
                val protocol: MailProtocol = protocolValues[protocolId]
                val sequenceNumber = prologueStream.readLong()
                val topic = prologueStream.readUTF()
                val envelope = prologueStream.readLengthPrefixBytes()
                val keyDerivation = prologueStream.readLengthPrefixBytes()
                val header = EnclaveMailHeaderImpl(sequenceNumber, topic, envelope, keyDerivation)
                Prologue(protocol, header, prologueBytes)
            } catch (e: EOFException) {
                error("Truncated prologue", e)
            }
            _prologue = prologue
            return prologue
        }

    private fun DataInputStream.readLengthPrefixBytes(): ByteArray? {
        val size = readUnsignedShort()
        return if (size == 0) null else ByteArray(size).also(::readFully)
    }

    val header: EnclaveMailHeaderImpl get() = prologue.header

    private fun error(customMessage: String? = null, cause: Exception? = null): Nothing {
        var message = if (customMessage != null) "$customMessage. " else ""
        message += "Corrupt stream or not Conclave Mail."
        throw MailDecryptionException(message, cause)
    }

    private val encryptedBuffer = ByteArray(Noise.MAX_PACKET_LEN) // Reused to hold encrypted packets.
    private val currentDecryptedBuffer = ByteArray(MAX_PACKET_PLAINTEXT_LENGTH) // Current decrypted packet.
    private var currentUserBytesIndex = 0 // How far through the decrypted packet we got.
    private var currentUserBytesLength = 0 // Real length of user bytes in currentDecryptedBuffer.

    /** To get [mark] back, wrap this stream in a [java.io.BufferedInputStream]. */
    override fun markSupported(): Boolean {
        return false
    }

    override fun read(): Int {
        maybeReadPrivateHeader()

        return if (ensureAvailablePacket()) {
            currentDecryptedBuffer[currentUserBytesIndex++].toInt() and 0xFF
        } else {
            -1
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        maybeReadPrivateHeader()

        if ((len > (b.size - off)) || off < 0 || len < 0) {
            throw IndexOutOfBoundsException("$off + $len >= ${b.size}")
        }
        if (len == 0) {
            return 0
        }

        val limit = off + len
        var index = off

        while (ensureAvailablePacket()) {
            val length = minOf(currentUserBytesLength - currentUserBytesIndex, limit - index)
            System.arraycopy(currentDecryptedBuffer, currentUserBytesIndex, b, index, length)
            currentUserBytesIndex += length
            index += length
            if (index == limit) {
                break
            }
        }

        return if (index == off) -1 else index - off
    }

    private fun ensureAvailablePacket(): Boolean {
        while (currentUserBytesIndex == currentUserBytesLength) {
            // We reached the end of the current in memory decrypted packet so read another from the stream.
            // We do this in a loop so that we can skip over packets which are just padding.
            readNextPacket()
        }
        // We reached the terminator packet and shouldn't read further.
        return currentUserBytesIndex != -1
    }

    private fun readNextPacket() {
        val cipherState = maybeHandshake()
        val input = `in`
        // Read the length, which includes the MAC tag.
        val packetLength: Int = readUnsignedShort()
        if (packetLength < cipherState.macLength)
            error("Packet length $packetLength is less than MAC length ${cipherState.macLength}")

        // Swallow the next packet, blocking until we got it.
        try {
            input.readExactlyNBytes(encryptedBuffer, packetLength)
        } catch (e: EOFException) {
            // We shouldn't run out of data before reaching the terminator packet, that could be a MITM attack.
            error("Stream ended without a terminator marker. Truncation can imply a MITM attack.")
        }

        // Now we can decrypt it.
        val plaintextLength = try {
            cipherState.decryptWithAd(null, encryptedBuffer, 0, currentDecryptedBuffer, 0, packetLength)
        } catch (e: Exception) {
            error(cause = e)
        }
        // The plaintext has a user bytes length field.
        if (plaintextLength < 2) {
            error("Invalid plaintext length of $plaintextLength")
        }

        val payloadLength = plaintextLength - 2
        // The user bytes length is the last two bytes of the plaintext (see writePacket).
        currentUserBytesLength = currentDecryptedBuffer.readShort(offset = payloadLength)
        if (currentUserBytesLength > payloadLength) {
            error("Invalid user bytes length")
        }
        // Check if this is the terminator packet, which is defined as a packet with no user bytes or padding
        currentUserBytesIndex = if (payloadLength == 0) -1 else 0
    }

    override fun skip(n: Long): Long {
        var toSkip = n
        var c = 0
        while (toSkip > 0) {
            if (read() == -1) return c.toLong()
            toSkip--
            c++
        }
        return c.toLong()
    }

    private fun maybeHandshake(): CipherState {
        cipherState?.let { return it }
        handshakeFailure?.let { throw it }
        try {
            // We ignore prologue extensions for forwards compatibility.
            setupHandshake(prologue).use { handshake ->
                _senderPublicKey = handshake.remotePublicKey.publicKey
                // Setup done, so retrieve the per-message key.
                val split: CipherStatePair = handshake.split()
                split.receiverOnly()
                check(handshake.action == HandshakeState.COMPLETE)
                val cipherState = split.receiver
                this.cipherState = cipherState
                return cipherState
            }
        } catch (e: Exception) {
            val handshakeFailure = if (e is MailDecryptionException) e else MailDecryptionException(e)
            this.handshakeFailure = handshakeFailure
            throw handshakeFailure
        } finally {
            // No longer need the private key now we've established the session key.
            privateKey = null
        }
    }

    /**
     * Lazily read the encrypted header, required due to lazy execution of handshake
     */
    private fun maybeReadPrivateHeader() {
        if (!privateHeaderRead) {
            readPrivateHeader()
        }
    }

    /**
     * Counterpart to readPrivateHeader in MailEncryptingStream
     * receives the number of bytes, followed by the byte buffer itself
     */
    private fun readPrivateHeader() {
        // Needs to be set ahead of receiving the buffer to prevent infinite recursion
        // This is required as readPrivateHeader is called lazily before the first read
        privateHeaderRead = true

        // If the mail protocol doesn't include the encrypted header, don't read it!
        if (prologue.protocol < MailProtocol.SENDER_KEY_TRANSMITTED_V2) {
            privateHeader = null
            return
        }

        // Attempt to read the private header
        try {
            privateHeader = when(val headerSize = readInt()) {
                0 -> null
                else -> readExactlyNBytes(headerSize)
            }
        } catch (e: EOFException) {
            privateHeaderRead = false
            error("Premature end of stream while reading encrypted header")
        } catch (e: Exception) {
            privateHeaderRead = false
            throw e
        }
    }

    private fun setupHandshake(prologue: Prologue): HandshakeState {
        val privateKey = checkNotNull(this.privateKey) { "Private key has not been provided to decrypt the stream." }
        val handshake = HandshakeState(prologue.protocol.noiseProtocolName, HandshakeState.RESPONDER)
        val localKeyPair = handshake.localKeyPair
        localKeyPair.setPrivateKey(privateKey.encoded, 0)
        // The prologue ensures the protocol name, headers and extensions weren't tampered with.
        handshake.setPrologue(prologue.raw, 0, prologue.raw.size)
        handshake.start()
        check(handshake.action == HandshakeState.READ_MESSAGE)
        val handshakeBuf = try {
            `in`.readExactlyNBytes(prologue.protocol.handshakeLength)
        } catch (e: EOFException) {
            error("Premature end of stream during handshake")
        }

        val payloadBuf = if (prologue.protocol == MailProtocol.SENDER_KEY_TRANSMITTED_V2) {
            ByteArray(1)
        } else {
            ByteArray(0)
        }
        try {
            handshake.readMessage(handshakeBuf, 0, handshakeBuf.size, payloadBuf, 0)
        } catch (e: AEADBadTagException) {
            error("The mail could not be decrypted due to either data corruption or key mismatch", e)
        }
        check(handshake.action == HandshakeState.SPLIT)
        return handshake
    }

    fun decryptKdsMail(kdsPrivateyKey: PrivateKey): DecryptedEnclaveMail = decryptMail(kdsPrivateyKey, isKdsKey = true)

    fun decryptMail(privateKey: PrivateKey): DecryptedEnclaveMail = decryptMail(privateKey, isKdsKey = false)

    private fun decryptMail(privateKey: PrivateKey, isKdsKey: Boolean): DecryptedEnclaveMail {
        // TODO: Optimise out copies here.
        //
        // We end up copying the mail every time it's read, the copy being defensive and thus useful only to protect
        // against malicious or buggy code inside the enclave. But as enclaves cannot load sandboxed code today,
        // it ends up being useless. We do it here ONLY to avoid it accidentally being overlooked later, when
        // we do indeed plan to introduce code sandboxing. Then it'd be unintuitive if you could pass an EnclaveMail
        // object into malicious code and the body or envelope comes back changed.
        //
        // What we should actually do is expose the MailDecryptingStream to the user (as an InputStream). Then they
        // can either pull from it directly e.g. via a serialisation lib, or copy to a byte array if they want to.
        // This would also let us avoid the memcopy into the enclave and consumption of EPC because the mail is
        // encrypted and authenticated in 64kb Noise packet blocks, so it's safe to hold it in unprotected memory
        // and store just the current decrypted block in EPC, which MailDecryptingStream already does. This would
        // also make it feasible to access huge mails without the 2GB size limit JVM arrays pose.
        setPrivateKey(privateKey)
        val mailBody = readBytes()
        return DecryptedEnclaveMail(
            header.sequenceNumber,
            header.topic,
            Curve25519PublicKey(senderPublicKey),
            header.envelope,
            privateHeader,
            mailBody,
            privateKey.takeIf { isKdsKey }
        )
    }

    companion object {
        private val protocolValues = MailProtocol.values()
    }
}
