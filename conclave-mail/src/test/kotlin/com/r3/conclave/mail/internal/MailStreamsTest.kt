package com.r3.conclave.mail.internal

import com.r3.conclave.internaltesting.throwableWithMailCorruptionErrorMessage
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.internal.noise.protocol.Noise
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.PrivateKey

class MailStreamsTest {
    companion object {
        private const val protocolNameX = "Noise_X_25519_AESGCM_SHA256"
        private const val protocolNameN = "Noise_N_25519_AESGCM_SHA256"
        private val receivingPrivateKey = Curve25519PrivateKey(ByteArray(32).also(Noise::random))
        private val receivingKeyState = Noise.createDH("25519").also { it.setPrivateKey(receivingPrivateKey.encoded, 0) }
        private val publicKey = Curve25519PublicKey(receivingKeyState.publicKey)

        private val msg = "Hello, can you hear me?".toByteArray()
    }

    @Test
    fun happyPath() {
        val bytes = encryptMessage()
        assertThat(MailProtocol.values()[bytes[0].toInt()].noiseProtocolName).isEqualTo(protocolNameN)
        // Can't find, it's encrypted.
        assertEquals(-1, String(bytes, Charsets.US_ASCII).indexOf("Hello"))
        val stream = decrypt(bytes)
        assertNull(stream.senderPublicKey)
        assertEquals(0, stream.headerBytes.size)
    }

    @Test
    fun largeStream() {
        // Test a "large" (50mb) encrypt/decrypt cycle. The data we'll encrypt is all zeros: that's fine.
        val data = ByteArray(1024 * 1024 * 50)
        val senderPrivateKey = ByteArray(32).also { Noise.random(it) }

        // Feed the byte array into the stream in 8kb pieces.
        val baos = ByteArrayOutputStream()
        MailEncryptingStream.wrap(baos, publicKey, null, Curve25519PrivateKey(senderPrivateKey)).use { encrypt ->
            ByteArrayInputStream(data).copyTo(encrypt, 8192)
        }

        // Now read the decrypting stream also in chunks.
        val decrypted = ByteArrayOutputStream()
        MailDecryptingStream(ByteArrayInputStream(baos.toByteArray()), receivingPrivateKey).use { decrypt ->
            decrypt.copyTo(decrypted, 8192)
        }

        assertArrayEquals(data, decrypted.toByteArray())
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
    fun senderKey() {
        val senderPrivateKey = ByteArray(32).also { Noise.random(it) }
        val senderDHState = Noise.createDH("25519").also { it.setPrivateKey(senderPrivateKey, 0) }
        val bytes = encryptMessage(Curve25519PrivateKey(senderPrivateKey))
        // When we authenticate ourselves, we use the X handshake pattern instead of the N pattern. X transmits the
        // public key corresponding to senderPrivateKey to the other side so they can use it as part of EC-DH.
        assertThat(MailProtocol.values()[bytes[0].toInt()].noiseProtocolName).isEqualTo(protocolNameX)
        val stream = decrypt(bytes)
        assertArrayEquals(senderDHState.publicKey, stream.senderPublicKey)
    }

    @Test
    fun headers() {
        val baos = ByteArrayOutputStream()
        val headerData = "header data".toByteArray()
        MailEncryptingStream.wrap(baos, publicKey, headerData, null).use { it.write(msg) }
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
        MailEncryptingStream.wrap(baos, publicKey, headerData, null).use { it.write(msg) }
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
        MailEncryptingStream.wrap(baos, publicKey, headerData, null).use { it.write(msg) }
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
        MailEncryptingStream.wrap(baos, publicKey, headerData, null).use { it.write(msg) }
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
        val encrypt = MailEncryptingStream.wrap(baos, publicKey, null, senderPrivateKey)
        encrypt.write(msg)
        encrypt.close()
        return baos.toByteArray()
    }
}