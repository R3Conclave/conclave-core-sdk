package com.r3.conclave.mail.internal

import com.r3.conclave.mail.internal.noise.protocol.CipherState
import com.r3.conclave.mail.internal.noise.protocol.HandshakeState
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.writeData
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.PrivateKey
import java.security.PublicKey

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
            // to encrypt with.
            val handshakeBytes = ByteArray(protocol.handshakeLength)

            val handshakeLen = if (protocol == MailProtocol.SENDER_KEY_TRANSMITTED_V2) {
                // We specify a payload of a single zero byte. This is irrelevant because being able to provide bytes
                // during the handshake is an optimisation mostly relevant for an interactive handshake where latency is
                // a primary concern. Enclaves have bigger performance issues to worry about. However we specify one anyway
                // because if we don't, Safari will crash inside JS crypto code because it can't encrypt the empty array.
                // Encrypting empty is a valid AES operation but Safari is buggy, and Safari is what gets used on Excel
                // for macOS, so we have to support it for ConclaveJS.
                val zero = byteArrayOf(0)
                handshake.writeMessage(handshakeBytes, 0, zero, 0, 1)
            } else {
                handshake.writeMessage(handshakeBytes, 0, null, 0, 0)
            }
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
