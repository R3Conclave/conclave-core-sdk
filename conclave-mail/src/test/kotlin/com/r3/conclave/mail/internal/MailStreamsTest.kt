package com.r3.conclave.mail.internal

import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.readFully
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
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
        private val senderPrivateKey = Curve25519PrivateKey.random()
        private val receivingPrivateKey = Curve25519PrivateKey.random()

        private val header = EnclaveMailHeaderImpl(
            sequenceNumber = 1,
            topic = "topic",
            envelope = Random.nextBytes(10),
            keyDerivation = null
        )
        private val msg = "Hello, can you hear me?".toByteArray()
        private val privateHeader = "None of your business!".toByteArray()

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

        @JvmStatic
        val privateHeaderSizes = intArrayOf(
            -1,     // null
            0,
            42,
            512 * 1024,
        )

        /**
         * Get configurations for parameterizable tests
         */
        @JvmStatic
        fun getTestConfigurations(): ArrayList<Arguments> {
            val configurations = ArrayList<Arguments>()
            for (dataSize in dataSizes) {
                for (privateHeaderSize in privateHeaderSizes) {
                    configurations.add(arguments(dataSize, privateHeaderSize))
                }
            }
            return configurations
        }
    }

    @Test
    fun `happy path`() {
        val bytes = encryptMessage()
        // Can't find, it's encrypted.
        assertEquals(-1, String(bytes, Charsets.US_ASCII).indexOf("Hello"))
        assertEquals(-1, String(bytes, Charsets.US_ASCII).indexOf("None"))
        assertEquals(2, getPacketCount(bytes))
        val stream = decrypt(bytes)
        assertThat(stream.header).isEqualTo(header)
    }

    @ParameterizedTest
    @MethodSource("getTestConfigurations")
    fun `single byte writes`(dataSize: Int, privateHeaderSize: Int) {
        testWrite(dataSize, privateHeaderSize) { encryptingStream, data ->
            for (b in data) {
                encryptingStream.write(b.toInt())
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getTestConfigurations")
    fun `write entire data at once`(dataSize: Int, privateHeaderSize: Int) {
        testWrite(dataSize, privateHeaderSize) { encryptingStream, data ->
            encryptingStream.write(data)
        }
    }

    @ParameterizedTest
    @MethodSource("getTestConfigurations")
    fun `write in chunks`(dataSize: Int, privateHeaderSize: Int) {
        testWrite(dataSize, privateHeaderSize) { encryptingStream, data ->
            data.inputStream().copyTo(encryptingStream, 8192)
        }
    }

    private fun testWrite(dataSize: Int, privateHeaderSize: Int, block: (MailEncryptingStream, ByteArray) -> Unit) {
        val senderPrivateKey = Curve25519PrivateKey.random()
        val data = ByteArray(dataSize).also(Noise::random)
        val privateHeader = when(privateHeaderSize < 0) {
            true -> null
            false -> ByteArray(privateHeaderSize).also(Noise::random)
        }

        val baos = ByteArrayOutputStream()
        MailEncryptingStream(baos, receivingPrivateKey.publicKey, header, privateHeader, senderPrivateKey, 0).use { encrypt ->
            block(encrypt, data)
        }
        val encrypted = baos.toByteArray()

        // Each packet should be max noise packet size, minus the remainder bytes and the terminator packet.
        val totalDataSize = data.size + maxOf(0, privateHeaderSize) + 4
        assertThat(getPacketCount(encrypted)).isEqualTo(ceil(totalDataSize.toDouble() / MAX_PACKET_PAYLOAD_LENGTH).toInt() + 1)

        val mds = MailDecryptingStream(encrypted, receivingPrivateKey)
        val decrypted = mds.readFully()
        assertArrayEquals(data, decrypted)

        // Zero length encrypted header is treated as null
        if (privateHeaderSize <= 0) {
            assertNull(mds.privateHeader)
        } else {
            assertArrayEquals(privateHeader, mds.privateHeader)
        }
    }

    @ParameterizedTest
    @MethodSource("getTestConfigurations")
    fun `single byte reads`(dataSize: Int, privateHeaderSize: Int) {
        testRead(dataSize, privateHeaderSize) { decryptingStream, out ->
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
    @MethodSource("getTestConfigurations")
    fun `read entire data at once`(dataSize: Int, privateHeaderSize: Int) {
        testRead(dataSize, privateHeaderSize) { decryptingStream, out ->
            val buffer = ByteArray(dataSize)
            val n = decryptingStream.read(buffer)
            assertThat(n).isEqualTo(dataSize)
            out.write(buffer)
        }
    }

    @ParameterizedTest
    @MethodSource("getTestConfigurations")
    fun `read in chunks`(dataSize: Int, privateHeaderSize: Int) {
        testRead(dataSize, privateHeaderSize) { decryptingStream, out ->
            val n = decryptingStream.copyTo(out, 8192)
            assertThat(n.toInt()).isEqualTo(dataSize)
        }
    }

    private fun testRead(dataSize: Int, privateHeaderSize: Int, block: (MailDecryptingStream, ByteArrayOutputStream) -> Unit) {
        val data = ByteArray(dataSize).also(Noise::random)
        val privateHeader = when(privateHeaderSize < 0) {
            true -> null
            false -> ByteArray(privateHeaderSize).also(Noise::random)
        }

        val encrypted = encryptMessage(message = data, privateHeader = privateHeader)

        val decryptingStream = MailDecryptingStream(encrypted, receivingPrivateKey)
        val out = ByteArrayOutputStream()
        block(decryptingStream, out)
        assertThat(decryptingStream.read()).isEqualTo(-1)
        decryptingStream.close()

        assertArrayEquals(data, out.toByteArray())

        // Zero length encrypted header is treated as null
        if (privateHeaderSize <= 0) {
            assertNull(decryptingStream.privateHeader)
        } else {
            assertArrayEquals(privateHeader, decryptingStream.privateHeader)
        }
    }

    @Test
    fun truncated() {
        val bytes = encryptMessage()
        for (truncatedSize in bytes.indices) {
            val truncated = bytes.copyOf(truncatedSize)
            assertThatThrownBy { decrypt(truncated) }
                .describedAs("Truncated size $truncatedSize")
                .isInstanceOf(MailDecryptionException::class.java)
                .hasMessageContaining("Corrupt stream or not Conclave Mail.")
        }
    }

    private fun decrypt(src: ByteArray): MailDecryptingStream {
        val decrypt = MailDecryptingStream(src, receivingPrivateKey)
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
        assertNotEquals(
            -1, String(encrypted).indexOf(header.topic),
            "Could not locate the unencrypted header data in the output bytes."
        )
        val stream = decrypt(encrypted)
        assertThat(stream.header).isEqualTo(header)
    }

    @Test
    fun `not able to read stream if private key not provided`() {
        val encrypted = encryptMessage()

        val decryptingStream = MailDecryptingStream(encrypted)
        assertThatThrownBy { decryptingStream.read() }
            .isInstanceOf(MailDecryptionException::class.java)
            .hasRootCauseMessage("Private key has not been provided to decrypt the stream.")
    }

    @Test
    fun `provide private key after reading header`() {
        val encrypted = encryptMessage()
        val decryptingStream = MailDecryptingStream(encrypted, privateKey = null)
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
        encrypted[topicIndex] = 'T'.code.toByte()

        val decryptingStream = MailDecryptingStream(encrypted)
        // The change goes unnoticed.
        assertThat(decryptingStream.header).isEqualTo(header.copy(topic = "Topic"))

        // But it should be detected as soon as the private key is supplied.
        assertThatThrownBy {
            decryptingStream.setPrivateKey(receivingPrivateKey)
        }.isInstanceOf(MailDecryptionException::class.java)
    }

    @ParameterizedTest
    @MethodSource("getDataSizes")
    fun `min size`(minSize: Int) {
        val encrypted = encryptMessage(minSize = minSize)
        assertThat(encrypted).hasSizeGreaterThanOrEqualTo(minSize)
        val stream = decrypt(encrypted)
        assertThat(stream.header).isEqualTo(header)
    }

    @Test
    fun `bounds check in write`() {
        // This test added for correct bounds check on MailStreams.write() to show that the
        // security vulnerability recorded in https://r3-cev.atlassian.net/browse/CON-192 has
        // been addressed.
        // We need to check the exception message to ensure it is our check that catches the problem
        // as a correctly operating JVM also throws IndexOutOfBoundsException by
        // default in this case when the actual underlying array is accessed.
        assertThatExceptionOfType(IndexOutOfBoundsException::class.java).isThrownBy {
            val plaintext = "This is my plaintext".encodeToByteArray()
            val senderPrivateKey = Curve25519PrivateKey.random()
            val baos = ByteArrayOutputStream()
            MailEncryptingStream(baos, receivingPrivateKey.publicKey, header, null, senderPrivateKey, 0).use { encrypt ->
                encrypt.write(plaintext, 0x7fffffff, 1)
            }
        }.withMessage("2147483647 + 1 >= 20")
    }

    @Test
    fun `bounds check in read`() {
        // This test added for correct bounds check on MailStreams.read() to address the
        // security note in https://r3-cev.atlassian.net/browse/CON-194 has been addressed.
        // We need to check the exception message to ensure it is our check that catches the problem
        // as a correctly operating JVM also throws IndexOutOfBoundsException by
        // default in this case when the actual underlying array is accessed.
        assertThatExceptionOfType(IndexOutOfBoundsException::class.java).isThrownBy {
            val data = ByteArray(16).also(Noise::random)
            val encrypted = encryptMessage(message = data)
            MailDecryptingStream(encrypted, receivingPrivateKey).use { decrypt ->
                val out = ByteArray(1)
                decrypt.read(out, 0x7fffffff, 1)
            }
        }.withMessage("2147483647 + 1 >= 1")
    }

    private fun encryptMessage(
        senderPrivateKey: PrivateKey = Companion.senderPrivateKey,
        header: EnclaveMailHeaderImpl = Companion.header,
        privateHeader: ByteArray? = Companion.privateHeader,
        minSize: Int = 0,
        message: ByteArray = msg
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val encrypt = MailEncryptingStream(baos, receivingPrivateKey.publicKey, header, privateHeader, senderPrivateKey, minSize)
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
