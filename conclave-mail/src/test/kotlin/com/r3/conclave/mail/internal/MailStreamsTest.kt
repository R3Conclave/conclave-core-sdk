package com.r3.conclave.mail.internal

import com.r3.conclave.internaltesting.throwableWithMailCorruptionErrorMessage
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.internal.MailEncryptingStream.Companion.MAX_PAYLOAD_LENGTH
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.readFully
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.security.PrivateKey
import kotlin.math.ceil

class MailStreamsTest {
    companion object {
        private const val protocolNameX = "Noise_X_25519_AESGCM_SHA256"
        private const val protocolNameN = "Noise_N_25519_AESGCM_SHA256"
        private val receivingPrivateKey = Curve25519PrivateKey(ByteArray(32).also(Noise::random))
        private val publicKey = receivingPrivateKey.publicKey

        private val msg = "Hello, can you hear me?".toByteArray()

        @JvmStatic
        val dataSizes = intArrayOf(
                0,
                1,
                MAX_PAYLOAD_LENGTH - 1,
                MAX_PAYLOAD_LENGTH + 0,
                MAX_PAYLOAD_LENGTH + 1,
                Noise.MAX_PACKET_LEN - 1,
                Noise.MAX_PACKET_LEN + 0,
                Noise.MAX_PACKET_LEN + 1,
                2 * MAX_PAYLOAD_LENGTH,
                50 * 1024 * 1024,
        )
    }

    @Test
    fun `happy path`() {
        val bytes = encryptMessage()
        assertThat(MailProtocol.values()[bytes[2].toInt()].noiseProtocolName).isEqualTo(protocolNameN)
        // Can't find, it's encrypted.
        assertEquals(-1, String(bytes, Charsets.US_ASCII).indexOf("Hello"))
        assertEquals(2, getPacketCount(bytes))
        val stream = decrypt(bytes)
        assertNull(stream.senderPublicKey)
        assertEquals(0, stream.headerBytes.size)
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
        MailEncryptingStream(baos, publicKey, null, senderPrivateKey).use { encrypt ->
            block(encrypt, data)
        }
        val encrypted = baos.toByteArray()

        // Each packet should be max noise packet size, minus the remainder bytes and the terminator packet.
        assertThat(getPacketCount(encrypted)).isEqualTo(ceil(data.size.toDouble() / MAX_PAYLOAD_LENGTH).toInt() + 1)

        val decrypted = MailDecryptingStream(encrypted.inputStream(), receivingPrivateKey).readFully()
        assertArrayEquals(data, decrypted)
    }

    @Test
    fun truncated() {
        val bytes = encryptMessage()
        val truncated = bytes.copyOf(bytes.size - 1)
        val e = assertThrows<IOException> { decrypt(truncated) }
        assertTrue("Truncated" in e.message!!, e.message!!)
    }

    private fun decrypt(src: ByteArray): MailDecryptingStream {
        val decrypt = MailDecryptingStream(src.inputStream(), receivingPrivateKey)
        val all = decrypt.readBytes()
        assertArrayEquals(msg, all)
        assertEquals(-1, decrypt.read())
        return decrypt
    }

    @Test
    fun `sender key`() {
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
        val baos = ByteArrayOutputStream()
        val headerData = "header data".toByteArray()
        MailEncryptingStream(baos, publicKey, headerData, null).use { it.write(msg) }
        val encrypted = baos.toByteArray()
        assertNotEquals(-1, String(encrypted).indexOf("header data"),
                "Could not locate the unencrypted header data in the output bytes.")
        val stream = decrypt(encrypted)
        assertArrayEquals(headerData, stream.headerBytes)
    }

    @Test
    fun `not able to read stream if private key not provided`() {
        val baos = ByteArrayOutputStream()
        val headerData = "header data".toByteArray()
        MailEncryptingStream(baos, publicKey, headerData, null).use { it.write(msg) }
        val encrypted = baos.toByteArray()

        val decryptingStream = MailDecryptingStream(encrypted.inputStream())
        assertThatIOException().isThrownBy {
            decryptingStream.read()
        }.withMessage("Private key has not been provided to decrypt the stream.")
    }

    @Test
    fun `provide private key after reading header`() {
        val baos = ByteArrayOutputStream()
        val headerData = "header data".toByteArray()
        MailEncryptingStream(baos, publicKey, headerData, null).use { it.write(msg) }
        val encrypted = baos.toByteArray()
        val decryptingStream = MailDecryptingStream(encrypted.inputStream(), privateKey = null)
        assertArrayEquals(headerData, decryptingStream.headerBytes)

        decryptingStream.setPrivateKey(receivingPrivateKey)
        val all = decryptingStream.readBytes()
        assertArrayEquals(msg, all)
        assertEquals(-1, decryptingStream.read())
    }

    @Test
    fun `setPrivateKey can detect corruption in header`() {
        val baos = ByteArrayOutputStream()
        val headerData = "header data".toByteArray()
        MailEncryptingStream(baos, publicKey, headerData, null).use { it.write(msg) }
        val encrypted = baos.toByteArray()

        val headerDataIndex = String(encrypted).indexOf("header data")
        encrypted[headerDataIndex] = 'H'.toByte()

        val decryptingStream = MailDecryptingStream(encrypted.inputStream())
        // The change goes unnoticed.
        assertArrayEquals("Header data".toByteArray(), decryptingStream.headerBytes)

        // But it should be detected as soon as the private key is supplied.
        assertThatThrownBy {
            decryptingStream.setPrivateKey(receivingPrivateKey)
        }.`is`(throwableWithMailCorruptionErrorMessage)
    }

    private fun encryptMessage(senderPrivateKey: PrivateKey? = null): ByteArray {
        val baos = ByteArrayOutputStream()
        val encrypt = MailEncryptingStream(baos, publicKey, null, senderPrivateKey)
        encrypt.write(msg)
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
