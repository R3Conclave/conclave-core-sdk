package com.r3.conclave.mail.internal

import com.r3.conclave.internaltesting.throwableWithMailCorruptionErrorMessage
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.internal.MailEncryptingStream.Companion.MAX_PACKET_PAYLOAD_LENGTH
import com.r3.conclave.mail.internal.MailEncryptingStream.Companion.MAX_PACKET_PLAINTEXT_LENGTH
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.readFully
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.security.PrivateKey
import kotlin.math.ceil
import kotlin.random.Random

class MailStreamsTest {
    companion object {
        private const val protocolNameX = "Noise_X_25519_AESGCM_SHA256"
        private val senderPrivateKey = Curve25519PrivateKey(ByteArray(32).also(Noise::random))
        private val receivingPrivateKey = Curve25519PrivateKey(ByteArray(32).also(Noise::random))

        private val header = EnclaveMailHeaderImpl(sequenceNumber = 1, topic = "topic", envelope = Random.nextBytes(10), keyDerivation = null)
        private val msg = "Hello, can you hear me?".toByteArray()

        @JvmStatic
        val dataSizes = intArrayOf(
                0,
                1,
                MAX_PACKET_PAYLOAD_LENGTH - 1,
                MAX_PACKET_PAYLOAD_LENGTH + 0,
                MAX_PACKET_PAYLOAD_LENGTH + 1,
                MAX_PACKET_PLAINTEXT_LENGTH + 0,
                MAX_PACKET_PLAINTEXT_LENGTH + 1,
                Noise.MAX_PACKET_LEN - 1,
                Noise.MAX_PACKET_LEN + 0,
                Noise.MAX_PACKET_LEN + 1,
                2 * MAX_PACKET_PAYLOAD_LENGTH,
                50 * 1024 * 1024,
        )
    }

    @Test
    fun `happy path`() {
        val bytes = encryptMessage()
        // Can't find, it's encrypted.
        assertEquals(-1, String(bytes, Charsets.US_ASCII).indexOf("Hello"))
        assertEquals(2, getPacketCount(bytes))
        val stream = decrypt(bytes)
        assertThat(stream.header).isEqualTo(header)
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `single byte writes`(dataSize: Int) {
        testWrite(dataSize) { encryptingStream, data ->
            for (b in data) {
                encryptingStream.write(b.toInt())
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `write entire data at once`(dataSize: Int) {
        testWrite(dataSize) { encryptingStream, data ->
            encryptingStream.write(data)
        }
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `write in chunks`(dataSize: Int) {
        testWrite(dataSize) { encryptingStream, data ->
            data.inputStream().copyTo(encryptingStream, 8192)
        }
    }

    private fun testWrite(dataSize: Int, block: (MailEncryptingStream, ByteArray) -> Unit) {
        val senderPrivateKey = Curve25519PrivateKey(ByteArray(32).also(Noise::random))
        val data = ByteArray(dataSize).also(Noise::random)

        val baos = ByteArrayOutputStream()
        MailEncryptingStream(baos, receivingPrivateKey.publicKey, header, senderPrivateKey, 0).use { encrypt ->
            block(encrypt, data)
        }
        val encrypted = baos.toByteArray()

        // Each packet should be max noise packet size, minus the remainder bytes and the terminator packet.
        assertThat(getPacketCount(encrypted)).isEqualTo(ceil(data.size.toDouble() / MAX_PACKET_PAYLOAD_LENGTH).toInt() + 1)

        val decrypted = MailDecryptingStream(encrypted.inputStream(), receivingPrivateKey).readFully()
        assertArrayEquals(data, decrypted)
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `single byte reads`(dataSize: Int) {
        testRead(dataSize) { decryptingStream, out ->
            while (true) {
                val b = decryptingStream.read()
                if (b == -1) {
                    assertThat(decryptingStream.read(ByteArray(0))).isEqualTo(0)
                    assertThat(decryptingStream.read(ByteArray(1))).isEqualTo(-1)
                    break
                }
                out.write(b)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `read entire data at once`(dataSize: Int) {
        testRead(dataSize) { decryptingStream, out ->
            val buffer = ByteArray(dataSize)
            val n = decryptingStream.read(buffer)
            assertThat(n).isEqualTo(dataSize)
            out.write(buffer)
        }
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `read in chunks`(dataSize: Int) {
        testRead(dataSize) { decryptingStream, out ->
            val n = decryptingStream.copyTo(out, 8192)
            assertThat(n.toInt()).isEqualTo(dataSize)
        }
    }

    private fun testRead(dataSize: Int, block: (MailDecryptingStream, ByteArrayOutputStream) -> Unit) {
        val data = ByteArray(dataSize).also(Noise::random)
        val encrypted = encryptMessage(message = data)

        val decryptingStream = MailDecryptingStream(encrypted.inputStream(), receivingPrivateKey)
        val out = ByteArrayOutputStream()
        block(decryptingStream, out)
        assertThat(decryptingStream.read()).isEqualTo(-1)
        decryptingStream.close()

        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun truncated() {
        val bytes = encryptMessage()
        for (truncatedSize in bytes.indices) {
            val truncated = bytes.copyOf(truncatedSize)
            assertThatIOException()
                    .describedAs("Truncated size $truncatedSize")
                    .isThrownBy { decrypt(truncated) }
                    .withMessageContaining("Corrupt stream or not Conclave Mail.")
        }
    }

    private fun decrypt(src: ByteArray): MailDecryptingStream {
        val decrypt = MailDecryptingStream(src.inputStream(), receivingPrivateKey)
        val all = decrypt.readBytes()
        assertArrayEquals(msg, all)
        assertEquals(-1, decrypt.read())
        return decrypt
    }

    @Test
    fun `sender private key`() {
        val senderPrivateKey = ByteArray(32).also { Noise.random(it) }
        val senderDHState = Noise.createDH("25519").also { it.setPrivateKey(senderPrivateKey, 0) }
        val bytes = encryptMessage(Curve25519PrivateKey(senderPrivateKey))
        // When we authenticate ourselves, we use the X handshake pattern instead of the N pattern. X transmits the
        // public key corresponding to senderPrivateKey to the other side so they can use it as part of EC-DH.
        assertThat(MailProtocol.values()[bytes[2].toInt()].noiseProtocolName).isEqualTo(protocolNameX)
        val stream = decrypt(bytes)
        assertArrayEquals(senderDHState.publicKey, stream.senderPublicKey)
    }

    @Test
    fun headers() {
        val encrypted = encryptMessage()
        assertNotEquals(-1, String(encrypted).indexOf(header.topic),
                "Could not locate the unencrypted header data in the output bytes.")
        val stream = decrypt(encrypted)
        assertThat(stream.header).isEqualTo(header)
    }

    @Test
    fun `not able to read stream if private key not provided`() {
        val encrypted = encryptMessage()

        val decryptingStream = MailDecryptingStream(encrypted.inputStream())
        assertThatIOException().isThrownBy {
            decryptingStream.read()
        }.withMessage("Private key has not been provided to decrypt the stream.")
    }

    @Test
    fun `provide private key after reading header`() {
        val encrypted = encryptMessage()
        val decryptingStream = MailDecryptingStream(encrypted.inputStream(), privateKey = null)
        assertThat(decryptingStream.header).isEqualTo(header)

        decryptingStream.setPrivateKey(receivingPrivateKey)
        val all = decryptingStream.readBytes()
        assertArrayEquals(msg, all)
        assertEquals(-1, decryptingStream.read())
    }

    @Test
    fun `setPrivateKey can detect corruption in header`() {
        val encrypted = encryptMessage(header = header)

        val topicIndex = String(encrypted).indexOf("topic")
        encrypted[topicIndex] = 'T'.toByte()

        val decryptingStream = MailDecryptingStream(encrypted.inputStream())
        // The change goes unnoticed.
        assertThat(decryptingStream.header).isEqualTo(header.copy(topic = "Topic"))

        // But it should be detected as soon as the private key is supplied.
        assertThatThrownBy {
            decryptingStream.setPrivateKey(receivingPrivateKey)
        }.`is`(throwableWithMailCorruptionErrorMessage)
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `min size`(minSize: Int) {
        val encrypted = encryptMessage(minSize = minSize)
        assertThat(encrypted).hasSizeGreaterThanOrEqualTo(minSize)
        val stream = decrypt(encrypted)
        assertThat(stream.header).isEqualTo(header)
    }

    private fun encryptMessage(senderPrivateKey: PrivateKey = Companion.senderPrivateKey,
                               header: EnclaveMailHeaderImpl = Companion.header,
                               minSize: Int = 0,
                               message: ByteArray = msg): ByteArray {
        val baos = ByteArrayOutputStream()
        val encrypt = MailEncryptingStream(baos, receivingPrivateKey.publicKey, header, senderPrivateKey, minSize)
        encrypt.write(message)
        encrypt.close()
        return baos.toByteArray()
    }

    private fun getPacketCount(bytes: ByteArray): Int {
        val dis = DataInputStream(bytes.inputStream())
        val prologueSize = dis.readUnsignedShort()
        val protocol = MailProtocol.values()[dis.read()]
        dis.skipExactly(prologueSize - 1)
        dis.skipExactly(protocol.handshakeLength)
        var count = 0
        while (dis.available() > 0) {
            val packetSize = dis.readUnsignedShort()
            dis.skipExactly(packetSize)
            count++
        }
        return count
    }

    private fun DataInputStream.skipExactly(n: Int) {
        if (skipBytes(n) != n) {
            throw EOFException()
        }
    }
}
