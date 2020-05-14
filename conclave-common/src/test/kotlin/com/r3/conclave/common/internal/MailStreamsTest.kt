package com.r3.conclave.common.internal

import com.r3.conclave.common.internal.noise.protocol.DHState
import com.r3.conclave.common.internal.noise.protocol.Noise
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class MailStreamsTest {
    private val protocolName = "Noise_X_25519_AESGCM_SHA256"
    private val receivingPrivateKey = ByteArray(32).also { Noise.random(it) }
    private val receivingKey = Noise.createDH("25519").also { it.setPrivateKey(receivingPrivateKey, 0) }

    private val msg = "Hello, can you hear me?".toByteArray()

    @Test
    fun happyPath() {
        val bytes = encryptMessage()
        assertEquals(protocolName, String(bytes.copyOfRange(1, 1 + protocolName.length), Charsets.US_ASCII))
        // Can't find, it's encrypted.
        assertEquals(-1, String(bytes, Charsets.US_ASCII).indexOf("Hello?"))
        val stream = decrypt(bytes)
        assertNull(stream.senderPublicKey)
        assertNull(stream.associatedData)
    }

    @Test
    fun largeStream() {
        // Test a "large" (50mb) encrypt/decrypt cycle. The data we'll encrypt is all zeros: that's fine.
        val data = ByteArray(1024 * 1024 * 50)
        val senderPrivateKey = ByteArray(32).also { Noise.random(it) }

        // Feed the byte array into the stream in 8kb pieces.
        val baos = ByteArrayOutputStream()
        MailEncryptingStream(baos, receivingKey.publicKey, null, senderPrivateKey).use { encrypt ->
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
        val bais = ByteArrayInputStream(src)
        val decrypt = MailDecryptingStream(bais, receivingPrivateKey)
        val all = decrypt.readBytes()
        assertArrayEquals(msg, all)
        assertEquals(-1, decrypt.read())
        return decrypt
    }

    private val corruptionErrors = listOf(
            "Unknown Noise DH algorithm",
            "Unknown Noise cipher algorithm",
            "Unknown Noise hash algorithm",
            "Corrupt stream or not Conclave Mail",
            "Premature end of stream",
            "Protocol name must have 5 components",
            "Tag mismatch!"
    )

    @Test
    fun corrupted() {
        val bytes = encryptMessage()
        // Corrupt every byte in the array and check we get an exception with a reasonable
        // error message for each.
        for (i in bytes.indices) {
            bytes[i] = bytes[i].inc()
            val e = assertThrows<IOException> { decrypt(bytes) }
            // Is the exception message in our list of acceptable/anticipated errors?
            assertTrue(corruptionErrors.none { e.message!! in it }, "Unrecognised error: ${e.message!!}")
            bytes[i] = bytes[i].dec()
            // Definitely not corrupted now. Kinda redundant check but heck, better spend the cycles on this than reddit.
            decrypt(bytes)
        }
    }

    @Test
    fun senderKey() {
        val senderPrivateKey = ByteArray(32).also { Noise.random(it) }
        val senderDHState = Noise.createDH("25519").also { it.setPrivateKey(senderPrivateKey, 0) }
        val bytes = encryptMessage(senderPrivateKey)
        val stream = decrypt(bytes)
        assertArrayEquals(senderDHState.publicKey, stream.senderPublicKey)
    }

    @Test
    fun associatedData() {
        val baos = ByteArrayOutputStream()
        val ad = ByteArray(4) { it.toByte() }
        MailEncryptingStream(baos, receivingKey.publicKey, ad, null).use { it.write(msg) }
        val stream = decrypt(baos.toByteArray())
        assertArrayEquals(ad, stream.associatedData)
    }

    private fun encryptMessage(senderPrivateKey: ByteArray? = null): ByteArray {
        val baos = ByteArrayOutputStream()
        val encrypt = MailEncryptingStream(baos, receivingKey.publicKey, null, senderPrivateKey,
                "AESGCM", "25519", "SHA256")
        assertEquals(protocolName, encrypt.protocolName)

        encrypt.write(msg)
        encrypt.close()
        return baos.toByteArray()
    }
}