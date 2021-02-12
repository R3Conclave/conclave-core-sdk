package com.r3.conclave.mail

import com.r3.conclave.internaltesting.throwableWithMailCorruptionErrorMessage
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MailTests {
    private companion object {
        val alice = Curve25519PrivateKey.random()
        val bob = Curve25519PrivateKey.random()

        val message1 = "rumours gossip nonsense misinformation fake news good stuff".toByteArray()
    }

    @Test
    fun featureCombinations() {
        encryptDecrypt(withHeaders = false, withEnvelope = false)
        encryptDecrypt(withHeaders = true, withEnvelope = false)
        encryptDecrypt(withHeaders = false, withEnvelope = true)
        encryptDecrypt(withHeaders = true, withEnvelope = true)
    }

    private fun encryptDecrypt(withHeaders: Boolean, withEnvelope: Boolean) {
        // Test the base case of sending mail from anonymous to Bob without any special headers.
        val sendingPostOffice = if (withHeaders) {
            PostOffice.create(bob.publicKey, alice, "stuff")
        } else {
            PostOffice.create(bob.publicKey)
        }
        val senderPublicKey = sendingPostOffice.senderPublicKey

        fun encrypt(): ByteArray {
            return sendingPostOffice.encryptMail(message1, "env".toByteArray().takeIf { withEnvelope })
        }

        fun assertHeader(of: EnclaveMailHeader) {
            assertEquals(if (withHeaders) "stuff" else "default", of.topic)
            assertEquals("env".takeIf { withEnvelope }, of.envelope?.let { String(it) })
        }

        val encrypted: ByteArray = encrypt()

        // Two encryptions of the same message encrypt to different byte arrays, even if the sequence number is the
        // same. It just means that you can't tell if the same content is being sent twice in a row.
        assertFalse(encrypted.contentEquals(encrypt()))
        // Now check we can read the headers in the encrypted mail without needing the private key.
        assertHeader(Mail.getUnauthenticatedHeader(encrypted))

        // Decrypt and check.
        val decrypted: EnclaveMail = PostOffice.create(senderPublicKey, bob, "topic").decryptMail(encrypted)
        assertArrayEquals(message1, decrypted.bodyAsBytes)
        assertHeader(decrypted)
        if (withHeaders) {
            assertEquals(alice.publicKey, decrypted.authenticatedSender)
        }

        assertThat(decrypted.authenticatedSender).isEqualTo(sendingPostOffice.senderPublicKey)
    }

    @Test
    fun `sequence numbers start from zero and increment by 1`() {
        val postOffice = PostOffice.create(bob.publicKey)
        assertThat(Mail.getUnauthenticatedHeader(postOffice.encryptMail(message1)).sequenceNumber).isEqualTo(0)
        assertThat(Mail.getUnauthenticatedHeader(postOffice.encryptMail(message1)).sequenceNumber).isEqualTo(1)
        assertThat(Mail.getUnauthenticatedHeader(postOffice.encryptMail(message1)).sequenceNumber).isEqualTo(2)
    }

    @ParameterizedTest
    // Disallow dots as they are often meaningful in queue names.
    @ValueSource(strings = ["no whitespace allowed", "!!!", "😂", "1234.5678"])
    fun `invalid topics`(topic: String) {
        assertThatIllegalArgumentException().isThrownBy { PostOffice.create(bob.publicKey, alice, topic) }
    }

    @Test
    fun corrupted() {
        val alicePostOffice = PostOffice.create(bob.publicKey, alice, "topic")
        val bobPostOffice = PostOffice.create(alice.publicKey, bob, "topic")
        val bytes = alicePostOffice.encryptMail(message1)
        // Corrupt every byte in the array and check we get an exception with a reasonable
        // error message for each.
        for (i in bytes.indices) {
            bytes[i]++
            assertThatThrownBy {
                bobPostOffice.decryptMail(bytes)
            }.`is`(throwableWithMailCorruptionErrorMessage)
            bytes[i]--
            // Definitely not corrupted now. Kinda redundant check but heck, better spend the cycles on this than reddit.
            bobPostOffice.decryptMail(bytes)
        }
    }

    @Test
    fun `fixed min size`() {
        val postOffice = PostOffice.create(bob.publicKey)
        postOffice.minSizePolicy = MinSizePolicy.fixedMinSize(10 * 1024)
        assertThat(postOffice.encryptMail(message1)).hasSizeGreaterThanOrEqualTo(10 * 1024)
        assertThat(postOffice.encryptMail(message1 + message1)).hasSizeGreaterThanOrEqualTo(10 * 1024)
    }

    @Test
    fun `largest seen min size`() {
        val postOffice = PostOffice.create(bob.publicKey)
        postOffice.minSizePolicy = MinSizePolicy.largestSeen()
        assertThat(postOffice.encryptMail(message1 + message1)).hasSizeGreaterThanOrEqualTo(2 * message1.size)
        assertThat(postOffice.encryptMail(message1)).hasSizeGreaterThanOrEqualTo(2 * message1.size)
    }

    @Test
    fun `moving average min size`() {
        val message2 = "Hello".toByteArray()
        val postOffice = PostOffice.create(bob.publicKey)
        postOffice.minSizePolicy = MinSizePolicy.movingAverage()
        assertThat(postOffice.encryptMail(message1)).hasSizeGreaterThanOrEqualTo(message1.size)
        assertThat(postOffice.encryptMail(message1 + message2)).hasSizeGreaterThanOrEqualTo((message1.size + message2.size) / 2)
    }
}
