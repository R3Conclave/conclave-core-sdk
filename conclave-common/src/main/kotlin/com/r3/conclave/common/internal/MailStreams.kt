package com.r3.conclave.common.internal

import com.r3.conclave.common.internal.noise.protocol.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.ShortBufferException

// Utils for encoding a 16 bit unsigned value in little endian.
private fun ByteArray.writeShortTo(offset: Int, value: Short) = writeShortTo(offset, value.toInt() and 0xFFFF)

private fun ByteArray.writeShortTo(offset: Int, value: Int) {
    this[offset] = (value shr 8).toByte()
    this[offset + 1] = value.toByte()
}

/**
 * A stream filter that encrypts the input data. Closing this stream writes a termination footer which protects the
 * result against truncation attacks, so you must remember to do so.
 *
 * Every write is encrypted as a separate length-prefixed block. This stream does not perform any rechunking. It will
 * refuse to accept single-byte writes, as that would create tons of 1-byte blocks that would be very inefficient.
 * Thus this stream should always be put behind a [java.io.BufferedOutputStream] configured with a bit less
 * than the [Noise.MAX_PACKET_LEN] as the buffer size.
 *
 * The associated data must fit in the Noise handshake packet after the keys are taken into account. With Curve25519
 * this means it must be less than ([Noise.MAX_PACKET_LEN] - (32 * 8) - (16 * 8) - 1) bytes, or 65151 bytes.
 *
 * You can provide your own private key as well as the recipient's public key. The recipient will receive your public
 * key and a proof that you encrypted the message. This isn't a typical digital signature but rather is based on
 * the properties of the Diffie-Hellman algorithm - see section 7.4 of
 * [the Noise specification](http://noiseprotocol.org/noise.html#handshake-pattern-basics) for information.
 *
 * The message is encrypted with a random key each time even though both destination public key and sender private
 * keys are (expected to be) static. In other words encrypting the same message twice will yield different outputs
 * each time and when writing tests you should treat the output as if it were a stream of random numbers i.e. don't
 * compare the output against a recorded output.
 *
 * This class is not thread safe and requires external synchronization.
 *
 * @param out                  The [OutputStream] to use.
 * @param cipherName           The Noise cipher name to use, "AESGCM" is the default.
 * @param dhName               The Noise Diffie-Hellman algorithm name, "25519" is the default.
 * @param hashName             The Noise hash algorithm name, "SHA256" is the default.
 * @param destinationPublicKey The public key to encrypt the stream to.
 * @param associatedData       If not null, unencrypted data that will be included in the header and authenticated.
 * @param senderPrivateKey     If not null, your private key. The recipient will receive your public key and be sure
 *                             you encrypted the message. Only provide one if it's stable and the public part would
 *                             be recognised or remembered by the enclave, otherwise a dummy key meaning 'anonymous'
 *                             will be used instead.
 */
internal class MailEncryptingStream(
        out: OutputStream,
        // TODO: Make keys type safe
        destinationPublicKey: ByteArray,
        associatedData: ByteArray?,
        senderPrivateKey: ByteArray?
) : FilterOutputStream(out) {
    private var cipherState: CipherState? = null
    private val destinationPublicKey: ByteArray = destinationPublicKey.clone()
    private val associatedData: ByteArray? = associatedData?.clone()
    private val senderPrivateKey: ByteArray? = senderPrivateKey?.clone()

    private val cipherName: String = "AESGCM"
    private val dhName: String = "25519"
    private val hashName: String = "SHA256"

    /** The standard Noise protocol name, as used in the specs. */
    val protocolName = (if (senderPrivateKey != null) "Noise_X_" else "Noise_N_") + "${dhName}_${cipherName}_$hashName"

    // If this hasn't been written to before, emit the necessary headers to set up the Diffie-Hellman "handshake".
    // The other party isn't here to "handshake" with us but that's OK because this is a non-interactive protocol:
    // they will complete it when reading the stream.
    private fun maybeHandshake() {
        if (cipherState != null) return  // Already set up the stream.

        // Noise can be used in various ways, identified by a string like this one. We always use the "X" handshake,
        // which means it's one way communication (receiver is entirely silent i.e. good for files).
        val protocolNameBytes = protocolName.toByteArray(StandardCharsets.US_ASCII)
        val handshake = HandshakeState(protocolName, HandshakeState.INITIATOR)
        try {
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

            // The prologue stops the Noise protocol name being tampered with to mismatch what we think we're using
            // with what the receiver thinks we're using.
            val prologue = computePrologue(protocolNameBytes)
            handshake.setPrologue(prologue, 0, prologue.size)
            handshake.start()
            check(handshake.action == HandshakeState.WRITE_MESSAGE)

            // Check size of the associated data (i.e. unencrypted but authenticated mail headers).
            val associatedDataLen = associatedData?.size ?: 0
            val maxADLen = Noise.MAX_PACKET_LEN - (localKeyPair?.publicKeyLength ?: 0) - Noise.createCipher(cipherName).macLength - 1
            if (associatedDataLen > maxADLen)
                throw IOException("The associated data is too large: $associatedDataLen but must be less than $maxADLen")

            // The initial bytes consist of a 8 bit name length prefix, the protocol name, then a 16 bit handshake length,
            // then the Noise handshake with the authenticated payload. headerBytes is sized 8kb larger than needed to
            // provide room for keys and hashes - this is more than necessary but it doesn't matter. We could precisely
            // pre-calculate how much space is required but it's not worth it: no elliptic curve algorithm will need more
            // than this and this way we avoid accidentally running out of space/off by one errors.
            val headerBytes = ByteArray(1 + protocolName.length + 2 + 8192 + associatedDataLen)

            // Set up length prefixed protocol name.
            check(protocolName.length < 256) { "${protocolName.length} < 256" }
            headerBytes[0] = protocolName.length.toByte()
            protocolNameBytes.copyInto(headerBytes, 1)

            // And now pass control to Noise to write out the Diffie-Hellman handshake that sets up the key to encrypt
            // with, passing the associated data and its length. It'll be written past the two bytes we reserved to
            // record the size.
            val handshakeLength = handshake.writeMessage(headerBytes, protocolNameBytes.size + 3,
                    associatedData, 0, associatedDataLen)

            // Write two bytes of length for the handshake, now we know how big it is.
            headerBytes.writeShortTo(1 + protocolNameBytes.size, handshakeLength)

            // Write the whole header to the output stream.
            val fullHeaderLength = 1 + protocolNameBytes.size + 2 + handshakeLength
            out.write(headerBytes, 0, fullHeaderLength)

            // Now we can request the ciphering object from Noise.
            check(handshake.action == HandshakeState.SPLIT)
            val split = handshake.split()
            split.senderOnly()   // One way not two way communication.
            cipherState = split.sender
            check(handshake.action == HandshakeState.COMPLETE)
        } finally {
            handshake.destroy()
        }
    }

    @Throws(IOException::class)
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
    @Throws(IOException::class)
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

    @Throws(IOException::class)
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
 * However when later bytes may change the meaning of earlier bytes you should fully consume the stream (until
 * [read] returns -1) before acting on it.
 *
 * You can access the associated (unencrypted but authenticated) data provided by the user by calling
 * [associatedData], which will read enough of the stream to provide the answer (blocking if necessary).
 *
 * Marks are not supported by this stream.
 */
internal class MailDecryptingStream(
        input: InputStream,
        privateKey: ByteArray
) : FilterInputStream(BufferedInputStream(input)) {
    private val privateKey: ByteArray = privateKey.clone()
    private var cipherState: CipherState? = null

    // Remember the exception we threw so we can throw it again if the user keeps trying to use the stream.
    private var handshakeFailure: IOException? = null

    /**
     * Reads sufficient data to return the user-provided associated/authenticated data from the handshake, or
     * null if none was found.
     */
    var associatedData: ByteArray? = null
        private set
        @Throws(IOException::class) get() {
            maybeHandshake()
            return field
        }

    /**
     * Returns the authenticated public key of the sender. This may be useful to understand who sent you the
     * data, if you know the sender's possible public keys in advance.
     */
    var senderPublicKey: ByteArray? = null
        private set
        @Throws(IOException::class) get() {
            maybeHandshake()
            return field
        }

    private val buffer = ByteArray(Noise.MAX_PACKET_LEN) // Reused to hold encrypted packets.
    private val currentDecryptedBuffer = ByteArray(Noise.MAX_PACKET_LEN) // Current decrypted packet.
    private var currentReadPos = 0 // How far through the decrypted packet we got.
    private var currentBufferLen = 0 // Real length of data in currentDecryptedBuffer.

    override fun markSupported(): Boolean {
        return false
    }

    @Throws(IOException::class)
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

    private fun readShort(): Int {
        val b1 = `in`.read().also { if (it == -1) error("Truncated stream") }
        val b2 = `in`.read().also { if (it == -1) error("Truncated stream") }
        return (b1 shl 8) or b2
    }

    @Throws(IOException::class)
    private fun startNextPacket() {
        // Read the length, which includes the MAC tag.
        val cipherState = cipherState!!
        val input = `in`
        val packetLen: Int = readShort()
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

    @Throws(IOException::class)
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

    @Throws(IOException::class)
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

    @Throws(IOException::class)
    private fun maybeHandshake() {
        if (cipherState != null) return
        if (handshakeFailure != null) throw handshakeFailure!!
        try {
            val input = `in`!!
            // Read and check the header, construct the handshake based on it.
            val protocolName = readProtocolNameHeader()
            val handshakeLen = readShort()
            if (handshakeLen <= 0 || handshakeLen > Noise.MAX_PACKET_LEN) error("Bad handshake length $handshakeLen")
            val handshakeBytes = ByteArray(handshakeLen)
            if (input.read(handshakeBytes) < handshakeLen) error("Premature end of stream whilst reading the handshake")
            setupHandshake(protocolName).use { handshake ->
                readHandshake(handshakeLen, handshakeBytes, handshake)
                // The sender has the option of whether to authenticate or not. If not, it'll be a Noise_N_ handshake.
                if (protocolName.startsWith("Noise_X_"))
                    senderPublicKey = handshake.remotePublicKey.publicKey
                check(handshake.action == HandshakeState.SPLIT)
                // Setup done, so retrieve the per-message key.
                val split: CipherStatePair = handshake.split()
                split.receiverOnly()
                cipherState = split.receiver
                check(handshake.action == HandshakeState.COMPLETE)
            }
        } catch (e: Exception) {
            handshakeFailure = IOException(e)
        } finally {
            // No longer need the private key now we've established the session key.
            Noise.destroy(privateKey)
        }
        if (handshakeFailure != null) throw handshakeFailure!!
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun setupHandshake(protocolName: String): HandshakeState {
        val handshake = HandshakeState(protocolName, HandshakeState.RESPONDER)
        val localKeyPair = handshake.localKeyPair
        localKeyPair.setPrivateKey(privateKey, 0)
        // The prologue ensures the protocol name wasn't tampered with.
        val prologue: ByteArray = computePrologue(protocolName.toByteArray(StandardCharsets.UTF_8))
        handshake.setPrologue(prologue, 0, prologue.size)
        handshake.start()
        check(handshake.action == HandshakeState.READ_MESSAGE)
        return handshake
    }

    @Throws(ShortBufferException::class, BadPaddingException::class, IOException::class)
    private fun readHandshake(handshakeLen: Int, handshakeBytes: ByteArray, handshake: HandshakeState) {
        val ad = ByteArray(Noise.MAX_PACKET_LEN)
        val len = handshake.readMessage(handshakeBytes, 0, handshakeLen, ad, 0)
        associatedData = if (len == 0) null else ad.copyOfRange(0, len)
    }

    @Throws(IOException::class)
    private fun readProtocolNameHeader(): String {
        // A byte followed by that many characters in ASCII.
        val input = `in`!!
        val protocolNameLen = input.read()
        if (protocolNameLen == -1) error("No Noise protocol name header found, corrupted stream most likely.")
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

    @Throws(IOException::class)
    private fun error(s: String): Nothing {
        throw IOException("$s. Corrupt stream or not Conclave Mail.")
    }
}

private fun computePrologue(protocolNameBytes: ByteArray): ByteArray {
    // We compute the prologue as in the Noise Socket spec (as of August 2019). The actual value doesn't really
    // matter as long as it contains the protocol name, which we need to ensure isn't tampered with. So we
    // use "NoiseSocketInit1" + <negotiation_len> + <negotiation data> which is currently just the protocol name.
    // Note that Noise Socket is like the Noise Protocol itself - it's not a real protocol, it's a recipe for
    // baking protocols. We use it here only to simplify future interop work. It's OK if the spec changes.
    val noiseSocketPrologueHeader = "NoiseSocketInit1".toByteArray(StandardCharsets.UTF_8)
    val prologue = ByteArray(noiseSocketPrologueHeader.size + 2 + protocolNameBytes.size)
    noiseSocketPrologueHeader.copyInto(prologue, 0)
    prologue.writeShortTo(noiseSocketPrologueHeader.size, protocolNameBytes.size)
    protocolNameBytes.copyInto(prologue, noiseSocketPrologueHeader.size + 2)
    return prologue
}
