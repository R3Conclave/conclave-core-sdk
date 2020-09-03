package com.r3.conclave.mail.internal

import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.internal.MailEncryptingStream.Companion.wrap
import com.r3.conclave.mail.internal.noise.protocol.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.BadPaddingException
import javax.crypto.ShortBufferException

// Utils for encoding a 16 bit unsigned value in little endian.
private fun ByteArray.writeShortTo(offset: Int, value: Int) {
    this[offset] = (value shr 8).toByte()
    this[offset + 1] = value.toByte()
}

/**
 * A stream filter that encrypts the input data. Closing this stream writes a termination footer which protects the
 * result against truncation attacks, so you must remember to do so.
 *
 * To get an instance use [wrap]. This will return an instance of this object wrapped with a [BufferedOutputStream] that
 * ensures every write to this stream is of the optimal size. This is necessary because every write is encrypted as a
 * separate block, which is inefficient and used naively will break random access.
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
 * @param senderPrivateKey     If not null, your private key. The recipient will receive your public key and be sure
 *                             you encrypted the message. Only provide one if it's stable and the public part would
 *                             be recognised or remembered by the enclave, otherwise a dummy key meaning 'anonymous'
 *                             will be used instead.
 */
internal class MailEncryptingStream private constructor(
        out: OutputStream,
        destinationPublicKey: ByteArray,
        header: ByteArray?,
        senderPrivateKey: ByteArray?
) : FilterOutputStream(out) {
    companion object {
        private const val cipherName: String = "AESGCM"
        private const val dhName: String = "25519"
        private const val hashName: String = "SHA256"

        /**
         * Creates a [MailEncryptingStream] wrapped with a [BufferedOutputStream]. The buffering ensures data is
         * packetized by Noise to the maximum packet size, thus reducing the overhead of the stream to the minimum
         * needed for the AES/GCM MAC tags, and enabling future support for seeking inside encrypted streams.
         */
        fun wrap(
                out: OutputStream,
                destinationPublicKey: ByteArray,
                header: ByteArray?,
                senderPrivateKey: ByteArray?
        ): OutputStream {
            // The buffer is the maximum Noise packet length minus the 16 byte MAC that each packet has. Thus the start of
            // each 64kb block of mail stream after the prologue+handshake can be calculated if you know how big the
            // handshake was.
            return BufferedOutputStream(MailEncryptingStream(out, destinationPublicKey, header, senderPrivateKey), Noise.MAX_PACKET_LEN - 16)
        }
    }

    private var cipherState: CipherState? = null
    private val destinationPublicKey: ByteArray = destinationPublicKey.clone()
    private val header: ByteArray? = header?.clone()
    private val senderPrivateKey: ByteArray? = senderPrivateKey?.clone()

    /** The standard Noise protocol name, as used in the specs. */
    private val protocolName = (if (senderPrivateKey != null) "Noise_X_" else "Noise_N_") + "${dhName}_${cipherName}_$hashName"

    // If this hasn't been written to before, emit the necessary headers to set up the Diffie-Hellman "handshake".
    // The other party isn't here to "handshake" with us but that's OK because this is a non-interactive protocol:
    // they will complete it when reading the stream.
    private fun maybeHandshake() {
        if (cipherState != null) return  // Already set up the stream.

        // Noise can be used in various modes, the protocol name is an ASCII string that identifies the settings.
        // We write it here. It looks like this: Noise_X_25519_AESGCM_SHA256
        HandshakeState(protocolName, HandshakeState.INITIATOR).use { handshake ->
            handshake.remotePublicKey.setPublicKey(destinationPublicKey, 0)

            // If one was provided, the recipient will get our public key in an authenticated manner i.e. we cannot
            // fake it because we need to use the corresponding private key when sending the message.
            //
            // The nature of the handshake changes when a sender private key is supplied: we use the X handshake instead
            // of the N handshake. These two types are described in the Noise specification.
            val localKeyPair: DHState? = handshake.localKeyPair
            if (senderPrivateKey != null) {
                // localKeyPair will be non-null due to the selection of the protocol name above.
                localKeyPair!!.setPrivateKey(senderPrivateKey, 0)
            }

            // The prologue can be set to any data we like. Noise won't do anything with it except incorporating the
            // hash into the handshake, thus authenticating it. We use it to achieve two things:
            // 1. It stops the Noise protocol name being tampered with to mismatch what we think we're using
            //    with what the receiver thinks we're using.
            // 2. It authenticates the header, whilst leaving it unencrypted (vs what would happen if we put it in a
            //    handshake payload).
            val prologue: ByteArray = computePrologue(protocolName, header)
            out.write(prologue)
            handshake.setPrologue(prologue, 0, prologue.size)
            handshake.start()
            check(handshake.action == HandshakeState.WRITE_MESSAGE)

            // Ask Noise to select an ephemeral key and calculate the Diffie-Hellman handshake that sets up the AES key
            // to encrypt with. We don't provide any initial payload, although we technically could. Being able to
            // provide bytes during the handshake is an optimisation mostly relevant for an interactive handshake where
            // latency is a primary concern. Enclaves have bigger performance issues to worry about.
            val handshakeBytes = ByteArray(lengthOfHandshake(protocolName))
            val handshakeLen = handshake.writeMessage(handshakeBytes, 0, null, 0, 0)
            check(handshakeLen == handshakeBytes.size)
            out.write(handshakeBytes, 0, handshakeLen)

            // Now we can request the ciphering object from Noise.
            check(handshake.action == HandshakeState.SPLIT)
            val split = handshake.split()
            split.senderOnly()   // One way not two way communication.
            cipherState = split.sender
            check(handshake.action == HandshakeState.COMPLETE)
        }
    }

    private fun computePrologue(protocolName: String, header: ByteArray?): ByteArray {
        // Format:
        // 1 byte - protocol name length
        // N bytes - protocol name
        // 2 bytes - caller specified header length
        // N bytes - caller header
        // 2 bytes - extension area used only by this class
        // N bytes - ignored (no extensions in this version)
        val headerLen = header?.size ?: 0
        val protocolNameBytes = protocolName.toByteArray(StandardCharsets.US_ASCII)
        val prologue = ByteArray(1 + protocolNameBytes.size + 2 + headerLen + 2)
        check(protocolNameBytes.size < 256)
        prologue[0] = protocolNameBytes.size.toByte()
        protocolNameBytes.copyInto(prologue, 1)
        prologue.writeShortTo(1 + protocolNameBytes.size, headerLen)
        header?.copyInto(prologue, 1 + protocolNameBytes.size + 2)
        // The final two bytes are implicitly zero.
        return prologue
    }

    override fun write(b: Int) {
        throw UnsupportedOperationException("Writing individual bytes at a time is extremely inefficient. Use write(byte[]) instead.")
    }

    private val buffer = ByteArray(Noise.MAX_PACKET_LEN)

    /**
     * Writes [len] bytes from the specified [b] array starting at [off] to this output stream.
     * **The length may not be larger than [Noise.MAX_PACKET_LEN]**. Attempts to
     * write larger arrays will throw a [ShortBufferException] wrapped in an [IOException].
     * Zero length writes are ignored.
     *
     * @param b   the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs.
     */
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) return
        maybeHandshake()
        val cipherState = cipherState!!
        // This method should really be able to process any arbitrary length, but when wrapped in a
        // BufferedOutputStream with an appropriately sized buffer it's not really necessary.
        if (len > Noise.MAX_PACKET_LEN - cipherState.macLength - 2)
            throw IOException(ShortBufferException())
        val encryptedLength = cipherState.encryptWithAd(null, b, off, buffer, 2, len)
        buffer.writeShortTo(0, encryptedLength)
        out.write(buffer, 0, encryptedLength + 2)
    }

    override fun close() {
        // Write the terminator packet: an encryption of the empty byte array. This lets the other side know we
        // intended to end the stream and there's no MITM maliciously truncating our packets.
        maybeHandshake()
        try {
            val cipherState = cipherState!!
            val macLength = cipherState.encryptWithAd(null, ByteArray(0), 0, buffer, 2, 0)
            check(macLength == cipherState.macLength)
            buffer.writeShortTo(0, macLength)
            out.write(buffer, 0, macLength + 2)
            out.flush()
        } catch (e: ShortBufferException) {
            throw IOException(e)
        }
        // And propagate the close.
        super.close()
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
 * read the user provided associated data ([headerBytes]), but it will not have been authenticated. This is useful if you
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
    init {
        require(input.markSupported())
    }

    private var cipherState: CipherState? = null

    // Remember the exception we threw so we can throw it again if the user keeps trying to use the stream.
    private var handshakeFailure: Throwable? = null

    /**
     * Returns the authenticated public key of the sender. This may be useful to understand who sent you the
     * data, if you know the sender's possible public keys in advance.
     */
    var senderPublicKey: ByteArray? = null
        private set
        get() {
            maybeHandshake()
            return field
        }

    // We have a separate flag to track whether the private key has been provided or not because we want to be able to
    // clear the key as soon as it's not needed.
    private var privateKeyProvided = privateKey != null

    /**
     * Provide the private key needed to decrypt the stream. If the header has alraedy been read this method will immediately
     * authenticate it.
     */
    fun setPrivateKey(privateKey: PrivateKey) {
        check(!privateKeyProvided) { "Private key has already been provoded." }
        this.privateKey = privateKey
        privateKeyProvided = true
        if (_prologue != null) {
            // If the header has already been read then make sure it's OK.
            maybeHandshake()
        }
    }

    private fun readUnsignedShort(): Int {
        val b1 = `in`.read().also { if (it == -1) error("Truncated stream") }
        val b2 = `in`.read().also { if (it == -1) error("Truncated stream") }
        return (b1 shl 8) or b2
    }

    internal class Prologue(val protocolName: String, val header: ByteArray, val extensions: ByteArray, val raw: ByteArray)

    private var _prologue: Prologue? = null
    /**
     * Access to the prologue data, without performing any authentication checks. To verify the prologue wasn't
     * tampered with you must complete the Noise handshake.
     */
    private val prologue: Prologue get() {
        _prologue?.let { return it }
        // The prologue is accessed on first read() (if not before) and so this will always be reading from the beginning
        // of the stream.
        //
        // See computePrologue for the format.
        val input = `in`
        input.mark(63356)
        val protocolName = readProtocolName()

        fun readBuf(): ByteArray {
            val len = readUnsignedShort()   // readUnsignedShort throws an exception if not enough bytes, and we allow 64kb of header.
            val buf = ByteArray(len)
            if (input.read(buf) < len) error("Premature end of stream whilst reading the prologue")
            return buf
        }

        val header = readBuf()
        val extensions = readBuf()
        // Now re-read the prologue into a contiguous byte array so it can be fed to Noise for hashing.
        val rawPrologueSize = 1 + protocolName.length + 2 + header.size + 2 + extensions.size
        input.reset()
        val rawPrologue = ByteArray(rawPrologueSize)
        input.read(rawPrologue)

        val prologue = Prologue(protocolName, header, extensions, rawPrologue)
        _prologue = prologue
        return prologue
    }

    val headerBytes: ByteArray get() = prologue.header

    val header: EnclaveMailHeaderImpl by lazy { EnclaveMailHeaderImpl.decode(headerBytes) }

    private fun readProtocolName(): String {
        // A byte followed by that many characters in ASCII.
        val input = `in`!!
        val protocolNameLen = input.read()
        if (protocolNameLen <= 0) error("No Noise protocol name header found")
        // Read it in and advance the stream.
        val protocolNameBytes = ByteArray(protocolNameLen)
        if (input.read(protocolNameBytes) != protocolNameBytes.size) error("Could not read protocol name")

        // We limit the handshakes to just two here, with the same ciphers. Adding new ciphers won't be forwards
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
        return when (val protocolName = String(protocolNameBytes, StandardCharsets.US_ASCII)) {
            "Noise_X_25519_AESGCM_SHA256", "Noise_N_25519_AESGCM_SHA256" -> protocolName
            else -> error("Unsupported Noise protocol name: $protocolName")
        }
    }

    private fun error(s: String): Nothing {
        throw IOException("$s. Corrupt stream or not Conclave Mail.")
    }

    private val buffer = ByteArray(Noise.MAX_PACKET_LEN) // Reused to hold encrypted packets.
    private val currentDecryptedBuffer = ByteArray(Noise.MAX_PACKET_LEN) // Current decrypted packet.
    private var currentReadPos = 0 // How far through the decrypted packet we got.
    private var currentBufferLen = 0 // Real length of data in currentDecryptedBuffer.

    /** To get [mark] back, wrap this stream in a [BufferedInputStream]. */
    override fun markSupported(): Boolean {
        return false
    }

    override fun read(): Int {
        maybeHandshake()
        if (currentReadPos == currentBufferLen) {
            // We reached the end of the current in memory decrypted packet so read another from the stream.
            startNextPacket()
        }
        return if (currentReadPos == -1)
            -1             // We reached the terminator packet and shouldn't read further.
        else
            currentDecryptedBuffer[currentReadPos++].toInt() and 0xFF
    }

    private fun startNextPacket() {
        // Read the length, which includes the MAC tag.
        val cipherState = cipherState!!
        val input = `in`
        val packetLen: Int = readUnsignedShort()
        if (packetLen < cipherState.macLength)
            error("Packet length $packetLen is less than MAC length ${cipherState.macLength}")

        // Swallow the next packet, blocking until we got it.
        var cursor = 0
        while (cursor < packetLen) {
            val c = input.read(buffer, cursor, packetLen - cursor)
            if (c == -1) {
                // We shouldn't run out of data before reaching the terminator packet, that could be a MITM attack.
                error("Stream ended without a terminator marker. Truncation can imply a MITM attack.")
            }
            cursor += c
        }

        // Now we can decrypt it.
        currentBufferLen = try {
            cipherState.decryptWithAd(null, buffer, 0, currentDecryptedBuffer, 0, packetLen)
        } catch (e: ShortBufferException) {
            // Data was possibly corrupted.
            throw IOException(e)
        } catch (e: BadPaddingException) {
            throw IOException(e)
        }
        // Have we reached the terminator packet?
        currentReadPos = if (currentBufferLen == 0) -1 else 0
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

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        // TODO: When we fully target Java 9+ replace with default InputStream.read(byte[], int, int)
        //       and use Objects.checkFromIndexSize(off, len, b.length);
        if (off + len > b.size || off < 0 || len < 0)
            throw IndexOutOfBoundsException("$off + $len >= ${b.size}")

        if (len == 0) {
            return 0
        }
        var c = read()
        if (c == -1) {
            return -1
        }
        b[off] = c.toByte()
        var i = 1
        try {
            while (i < len) {
                c = read()
                if (c == -1) {
                    break
                }
                b[off + i] = c.toByte()
                i++
            }
        } catch (ee: IOException) {
            // See the spec for InputStream.read(byte[], int, int) to understand this empty catch block.
        }
        return i
    }

    private fun maybeHandshake() {
        if (cipherState != null) return
        handshakeFailure?.let { throw it }
        try {
            // We ignore prologue extensions for forwards compatibility.
            setupHandshake(prologue).use { handshake ->
                // The sender has the option of whether to authenticate or not. If not, it'll be a Noise_N_ handshake.
                if (prologue.protocolName.startsWith("Noise_X_"))
                    senderPublicKey = handshake.remotePublicKey.publicKey
                // Setup done, so retrieve the per-message key.
                val split: CipherStatePair = handshake.split()
                split.receiverOnly()
                cipherState = split.receiver
                check(handshake.action == HandshakeState.COMPLETE)
            }
        } catch (e: Exception) {
            handshakeFailure = if (e is IOException) e else IOException(e)
        } finally {
            // No longer need the private key now we've established the session key.
            privateKey = null
        }
        handshakeFailure?.let { throw it }
    }

    private fun setupHandshake(prologue: Prologue): HandshakeState {
        val privateKey = this.privateKey ?: throw IOException("Private key has not been provided to decrypt the stream.")
        val handshake = HandshakeState(prologue.protocolName, HandshakeState.RESPONDER)
        val localKeyPair = handshake.localKeyPair
        localKeyPair.setPrivateKey(privateKey.encoded, 0)
        // The prologue ensures the protocol name, headers and extensions weren't tampered with.
        handshake.setPrologue(prologue.raw, 0, prologue.raw.size)
        handshake.start()
        check(handshake.action == HandshakeState.READ_MESSAGE)
        val handshakeBuf = ByteArray(lengthOfHandshake(prologue.protocolName))
        `in`.read(handshakeBuf)
        val payloadBuf = ByteArray(0)
        handshake.readMessage(handshakeBuf, 0, handshakeBuf.size, payloadBuf, 0)
        check(handshake.action == HandshakeState.SPLIT)
        return handshake
    }

    /**
     * Use the given lambda to derive the encryption key for this stream from the header and fully decrypt it into an
     * authenticated [EnclaveMail] object.
     *
     * It is the caller's responsibility to close this stream.
     */
    fun decryptMail(deriveKey: (ByteArray?) -> PrivateKey): EnclaveMail {
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
        return object : EnclaveMail {
            override val bodyAsBytes: ByteArray get() = mailBody.copyOf()
            override val authenticatedSender: PublicKey? = senderPublicKey?.let(::Curve25519PublicKey)
            override val sequenceNumber: Long = header.sequenceNumber
            override val topic: String = header.topic
            override val from: String? = header.from
            override val envelope: ByteArray? get() = header.envelope?.copyOf()
        }
    }
}

/**
 * Returns how many bytes a Noise handshake for the given protocol name requires. These numbers come from the sizes
 * needed for the ephemeral Curve25519 public keys, AES/GCM MAC tags and encrypted static keys.
 *
 * For example: 48 == 32 (pubkey) + 16 (mac of an empty encrypted block)
 *
 * When no payload is specified Noise uses encryptions of the empty block (i.e. only the authentication hash tag is
 * emitted) as a way to authenticate the handshake so far.
 */
private fun lengthOfHandshake(protocolName: String): Int = when (protocolName) {
    "Noise_X_25519_AESGCM_SHA256" -> 96
    "Noise_N_25519_AESGCM_SHA256" -> 48
    else -> throw IllegalStateException("Unknown protocol name '$protocolName'")
}