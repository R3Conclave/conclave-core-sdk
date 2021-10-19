package com.r3.conclave.mail.internal

import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.mail.internal.MailEncryptingStream.Companion.MAX_PACKET_PLAINTEXT_LENGTH
import com.r3.conclave.mail.internal.noise.protocol.CipherState
import com.r3.conclave.mail.internal.noise.protocol.CipherStatePair
import com.r3.conclave.mail.internal.noise.protocol.HandshakeState
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.dataStream
import com.r3.conclave.utilities.internal.readExactlyNBytes
import com.r3.conclave.utilities.internal.writeData
import java.io.*
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.AEADBadTagException

// Utils for encoding a 16 bit unsigned value in big endian.
private fun ByteArray.writeShort(offset: Int, value: Int) {
    this[offset] = (value shr 8).toByte()
    this[offset + 1] = value.toByte()
}

private fun OutputStream.writeShort(value: Int) {
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArray.readShort(offset: Int): Int {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    return (b1 shl 8) or b2
}

private fun OutputStream.writeInt(value: Int) {
    write((value ushr 24) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

private fun InputStream.readInt(): Int {
    val b1 = (read() and 0xFF)
    val b2 = (read() and 0xFF)
    val b3 = (read() and 0xFF)
    val b4 = (read() and 0xFF)
    return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

/**
 * A stream filter that encrypts the input data. Closing this stream writes a termination footer which protects the
 * result against truncation attacks, so you must remember to do so.
 *
 * You can provide your own private key as well as the recipient's public key. The recipient will receive your public
 * key and a proof that you encrypted the message. This isn't a typical digital signature but rather is based on
 * the properties of the Diffie-Hellman algorithm - see section 7.4 of
 * [the Noise specification](http://noiseprotocol.org/noise.html#handshake-pattern-basics) for information.
 *
 * You can provide a header which will be emitted unencrypted to the stream. If the sender is authenticated then this
 * header is also authenticated, to the recipient only. There's no way for someone without the destination private key
 * to check if the header has been tampered with, although it can be extracted.
 *
 * The message is encrypted with a random key each time even though both destination public key and sender private
 * keys are (expected to be) static. In other words encrypting the same message twice will yield different outputs
 * each time and when writing tests you should treat the output as if it were a stream of random numbers i.e. don't
 * compare the output against a recorded output.
 *
 * This class is not thread safe and requires external synchronization.
 *
 * @param out                  The [OutputStream] to use.
 * @param destinationPublicKey The public key to encrypt the stream to.
 * @param header               If not null, unencrypted data that will be included and authenticated.
 * @param senderPrivateKey     Your private key. The recipient will receive your public key and be sure
 *                             you encrypted the message.
 * @param minSize              Pad the end of the stream to make sure the number of encrypted bytes is at least this amount.
 */
class MailEncryptingStream(
    out: OutputStream,
    private val destinationPublicKey: PublicKey,
    private val header: EnclaveMailHeaderImpl,
    private val privateHeader: ByteArray?,
    private val senderPrivateKey: PrivateKey,
    private val minSize: Int
) : FilterOutputStream(out) {
    companion object {
        internal const val MAX_PACKET_PLAINTEXT_LENGTH = Noise.MAX_PACKET_LEN - 16

        // The payload defined as the user bytes plus any padding of zeros. It does not include the length of the user
        // bytes, even though that is encrypted with the payload.
        internal const val MAX_PACKET_PAYLOAD_LENGTH = MAX_PACKET_PLAINTEXT_LENGTH - 2
    }

    private val cipherState: CipherState
    private val buffer = ByteArray(Noise.MAX_PACKET_LEN)
    private var bufferPosition = 0
    private var payloadBytesWritten = 0

    init {
        cipherState = handshake()
        writePrivateHeader()
    }

    // Emit the necessary headers to set up the Diffie-Hellman "handshake".
    // The other party isn't here to "handshake" with us but that's OK because this is a non-interactive protocol:
    // they will complete it when reading the stream.
    private fun handshake(): CipherState {
        val protocol = MailProtocol.SENDER_KEY_TRANSMITTED_V2
        // Noise can be used in various modes, the protocol name is an ASCII string that identifies the settings.
        // We write it here. It looks like this: Noise_X_25519_AESGCM_SHA256
        return HandshakeState(protocol.noiseProtocolName, HandshakeState.INITIATOR).use { handshake ->
            handshake.remotePublicKey.setPublicKey(destinationPublicKey.encoded, 0)

            // If one was provided, the recipient will get our public key in an authenticated manner i.e. we cannot
            // fake it because we need to use the corresponding private key when sending the message.
            //
            // The nature of the handshake changes when a sender private key is supplied: we use the X handshake instead
            // of the N handshake. These two types are described in the Noise specification.
            val localKeyPair = handshake.localKeyPair
            localKeyPair.setPrivateKey(senderPrivateKey.encoded, 0)

            // The prologue can be set to any data we like. Noise won't do anything with it except incorporating the
            // hash into the handshake, thus authenticating it. We use it to achieve two things:
            // 1. It stops the Noise protocol name being tampered with to mismatch what we think we're using
            //    with what the receiver thinks we're using.
            // 2. It authenticates the header, whilst leaving it unencrypted (vs what would happen if we put it in a
            //    handshake payload).
            val prologue: ByteArray = computePrologue(protocol)
            out.writeShort(prologue.size)
            out.write(prologue)
            handshake.setPrologue(prologue, 0, prologue.size)
            handshake.start()
            check(handshake.action == HandshakeState.WRITE_MESSAGE)

            // Ask Noise to select an ephemeral key and calculate the Diffie-Hellman handshake that sets up the AES key
            // to encrypt with. We don't provide any initial payload, although we technically could. Being able to
            // provide bytes during the handshake is an optimisation mostly relevant for an interactive handshake where
            // latency is a primary concern. Enclaves have bigger performance issues to worry about.
            val handshakeBytes = ByteArray(protocol.handshakeLength)
            val handshakeLen = handshake.writeMessage(handshakeBytes, 0, null, 0, 0)
            check(handshakeLen == handshakeBytes.size)
            out.write(handshakeBytes, 0, handshakeLen)

            // Now we can request the ciphering object from Noise.
            check(handshake.action == HandshakeState.SPLIT)
            val split = handshake.split()
            split.senderOnly()   // One way not two way communication.
            val cipherState = split.sender
            check(handshake.action == HandshakeState.COMPLETE)
            cipherState
        }
    }

    /**
     * Send the encrypted header.
     * First write the size of the buffer, then the bytes of the buffer itself.
     */
    private fun writePrivateHeader() {
        if (privateHeader == null) {
            writeInt(0)
        } else {
            writeInt(privateHeader.size)
            if (privateHeader.isNotEmpty()) {
                write(privateHeader)
            }
        }
    }

    /**
     * The prologue format is the protocol ID followed by the fields in the user header. It's written out prefixed by its
     * size which allows new fields to be added to the end.
     */
    private fun computePrologue(protocol: MailProtocol): ByteArray {
        return writeData {
            writeByte(protocol.ordinal)
            header.encodeTo(this)
        }
    }

    override fun write(b: Int) {
        buffer[bufferPosition++] = b.toByte()
        writePacketIfBufferFull()
    }

    /**
     * Encrypts [len] bytes from the specified [b] array starting at [off]. The encrypted bytes are only written to the
     * underlying stream until a certain packet chuck size is reached. This means a call to write may not involve I/O.
     * It's essential that [close] be called when no more bytes need to be encrypted.
     *
     * @param b   the plaintext data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs.
     */
    override fun write(b: ByteArray, off: Int, len: Int) {
        if ((off < 0) || (off > b.size) || (len < 0) ||
            ((off + len) > b.size) || ((off + len) < 0)
        ) {
            throw IndexOutOfBoundsException("$off + $len >= ${b.size}")
        }
        val endOffset = off + len
        var currentOffset = off
        while (true) {
            val remainingLength = endOffset - currentOffset
            if (remainingLength == 0) break
            val length = remainingLength.coerceAtMost(MAX_PACKET_PAYLOAD_LENGTH - bufferPosition)
            System.arraycopy(b, currentOffset, buffer, bufferPosition, length)
            bufferPosition += length
            writePacketIfBufferFull()
            currentOffset += length
        }
    }

    private fun writePacketIfBufferFull() {
        if (bufferPosition == MAX_PACKET_PAYLOAD_LENGTH) {
            writePacket()
        }
    }

    override fun close() {
        // First write out any remaining user bytes.
        if (bufferPosition > 0) {
            writePacket()
        }
        // Then continually write out padding packets (which contain no user bytes) until the required minSize is reached.
        // Having identical packets with just zeros is not a problem since Noise makes sure there is a unique IV for each
        // packet.
        while (payloadBytesWritten < minSize) {
            writePacket()
        }
        // Finally write the terminator packet: an encryption of an empty payload. This lets the other side know we
        // intended to end the stream and there's no MITM maliciously truncating our packets.
        writePacket()
        // And propagate the close.
        super.close()
    }

    /**
     * Write out an encrypted packet of the current state of the buffer, with added padding where necessary. The length
     * of the user bytes is appended to the end of the padding and is encrypted as well. The packet is prefixed by the
     * ciphertext length .
     */
    private fun writePacket() {
        val remainingPayloadLength = MAX_PACKET_PAYLOAD_LENGTH - bufferPosition
        val paddingLength = (minSize - payloadBytesWritten).coerceIn(0, remainingPayloadLength)
        // Append zeros to the end as padding towards reaching minSize.
        val payloadLength = bufferPosition + paddingLength
        buffer.fill(0, fromIndex = bufferPosition, toIndex = payloadLength)
        // Append the user bytes length to the end of the payload so that it's encrypted with it.
        buffer.writeShort(offset = payloadLength, value = bufferPosition)
        val plaintextLength = payloadLength + 2
        val encryptedLength = cipherState.encryptWithAd(null, buffer, 0, buffer, 0, plaintextLength)
        check(encryptedLength == plaintextLength + cipherState.macLength)
        out.writeShort(encryptedLength)
        out.write(buffer, 0, encryptedLength)
        out.flush()
        payloadBytesWritten += payloadLength
        bufferPosition = 0  // Reset the buffer for the next packet.
    }
}

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

    /** To get [mark] back, wrap this stream in a [BufferedInputStream]. */
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

        val payloadBuf = ByteArray(0)
        try {
            handshake.readMessage(handshakeBuf, 0, handshakeBuf.size, payloadBuf, 0)
        } catch (e: AEADBadTagException) {
            error("The mail could not be decrypted due to either data corruption or key mismatch", e)
        }
        check(handshake.action == HandshakeState.SPLIT)
        return handshake
    }

    /**
     * Use the given lambda to derive the encryption key for this stream from the header and fully decrypt it into an
     * authenticated [EnclaveMail] object.
     *
     * It is the caller's responsibility to close this stream.
     */
    fun decryptMail(deriveKey: (ByteArray?) -> PrivateKey): DecryptedEnclaveMail {
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
        val privateKey = deriveKey(header.keyDerivation)
        setPrivateKey(privateKey)
        val mailBody = readBytes()
        return DecryptedEnclaveMail(
            header.sequenceNumber,
            header.topic,
            Curve25519PublicKey(senderPublicKey),
            header.envelope,
            privateHeader,
            mailBody
        )
    }

    companion object {
        private val protocolValues = MailProtocol.values()
    }
}

// For now we have one handshake. Any new ones will have to use the same ciphers. Adding new ciphers won't be forwards
// compatible. The justification is as follows. Curve25519, AESGCM and SHA256 are by this point mature,
// well tested algorithms that are widely deployed to protect all internet traffic. Although new ciphers
// are regularly designed, in practice few are every deployed because elliptic curve crypto with AES and SHA2
// have no known problems, there are none on the horizon beyond quantum computers, and the places where other
// algorithms get deployed tend to be devices with severe power or space constraints.
//
// What of QC? Noise does support a potential post-quantum algorithm. However, no current PQ ciphersuite has
// been settled on by standards bodies yet, and there are multiple competing proposals, many of which have
// worse performance or other problems compared to the standard algorithms. Given that the QCs being built
// at the moment are said to be unusable for breaking encryption keys, and it's unclear when - if ever - such
// a machine will actually be built, it makes sense to wait for PQ standardisation and then implement the
// winning algorithms at that time, rather than attempt to second guess and end up with (relatively speaking)
// not well reviewed algorithms baked into the protocol.
enum class MailProtocol(
    val noiseProtocolName: String,
    /**
     * Returns how many bytes a Noise handshake for the given protocol name requires. These numbers come from the sizes
     * needed for the ephemeral Curve25519 public keys, AES/GCM MAC tags and encrypted static keys.
     *
     * For example: 48 == 32 (pubkey) + 16 (mac of an empty encrypted block)
     *
     * When no payload is specified Noise uses encryptions of the empty block (i.e. only the authentication hash tag is
     * emitted) as a way to authenticate the handshake so far.
     */
    val handshakeLength: Int
) {
    SENDER_KEY_TRANSMITTED("Noise_X_25519_AESGCM_SHA256", 96),

    /**
     * Identical to SENDER_KEY_TRANSMITTED but also supports the private header, an encrypted block that appears in the
     * stream immediately following the handshake.
     *
     * MailDecryptionStream instances supporting this protocol will also accept mail items using the previous one
     * (SENDER_KEY_TRANSMITTED). When doing so, the private header will be null.
     */
    SENDER_KEY_TRANSMITTED_V2("Noise_X_25519_AESGCM_SHA256", 96),
}
