package com.r3.conclave.host

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.kotlin.deliverMail
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.EnclaveMailId
import com.r3.conclave.mail.Mail
import com.r3.conclave.mail.MutableMail
import com.r3.conclave.mail.internal.Curve25519KeyPairGenerator
import com.r3.conclave.mail.internal.Curve25519PublicKey
import com.r3.conclave.testing.MockHost
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MailHostTest {
    companion object {
        private val messageBytes = "message".toByteArray()
    }

    private val keyPair = Curve25519KeyPairGenerator().generateKeyPair()
    private val echo by lazy { MockHost.loadMock<MailEchoEnclave>() }
    private val noop by lazy { MockHost.loadMock<NoopEnclave>() }

    @Test
    fun `encrypt and deliver mail`() {
        echo.start(null, null, null)
        val mail: MutableMail = buildMail(echo)
        var response: ByteArray? = null
        echo.deliverMail(1, mail.encrypt()) { bytes ->
            response = bytes
            null  // No response back to enclave.
        }
        response!!.deserialise {
            assertArrayEquals(messageBytes, readIntLengthPrefixBytes())
            assertEquals(1, readInt())
        }
    }

    @Test
    fun `deliver mail and answer enclave`() {
        echo.start(null, null, null)
        val mail: MutableMail = buildMail(echo)
        // In response to the delivered mail, the enclave sends us a local message, and we send a local message back.
        // It asserts the answer we give is as expected.
        echo.deliverMail(1, mail.encrypt()) { "an answer".toByteArray() }
    }

    @Test
    fun `mail acknowledgement`() {
        var acknowledgementID: EnclaveMailId? = null
        echo.start(null, null, object : EnclaveHost.MailCallbacks {
            override fun acknowledgeMail(mailID: EnclaveMailId) {
                acknowledgementID = mailID
            }
        })
        val mail: MutableMail = buildMail(echo)
        // First delivery doesn't acknowledge because we don't tell it to.
        echo.deliverMail(1, mail.encrypt()) { null }
        assertNull(acknowledgementID)
        // Try again and this time we'll get an ack.
        mail.incrementSequenceNumber()
        echo.deliverMail(2, mail.encrypt()) { "acknowledge".toByteArray() }
        assertEquals(2, acknowledgementID!!)
    }

    @Test
    fun `sequence numbers`() {
        // Verify that the enclave rejects a replay of the same message, or out of order delivery.
        noop.start(null, null, null)
        val encrypted1 = buildMail(noop).encrypt()
        val encrypted2 = buildMail(noop, "message 2".toByteArray()).also { it.sequenceNumber = 2 }.encrypt()
        val encrypted3 = buildMail(noop, "message 3".toByteArray()).also { it.sequenceNumber = 3 }.encrypt()
        val encrypted50 = buildMail(noop, "message 3".toByteArray()).also { it.sequenceNumber = 50 }.encrypt()
        // Cannot deliver message 2 twice even with different IDs.
        noop.deliverMail(100, encrypted2)
        var msg = assertThrows<RuntimeException> { noop.deliverMail(100, encrypted2) }.message!!
        assertTrue("Highest sequence number seen is 2, attempted delivery of 2" in msg) { msg }
        // Cannot now deliver message 1 because the sequence number would be going backwards.
        msg = assertThrows<RuntimeException> { noop.deliverMail(100, encrypted1) }.message!!
        assertTrue("Highest sequence number seen is 2, attempted delivery of 1" in msg) { msg }
        // Can deliver message 3
        noop.deliverMail(101, encrypted3)
        // Seq nums may not have gaps.
        msg = assertThrows<RuntimeException> { noop.deliverMail(102, encrypted50) }.message!!
        assertTrue("Highest sequence number seen is 3, attempted delivery of 50" in msg) { msg }

        // Seq nums of different topics are independent
        val secondTopic = buildMail(noop).also { it.topic = "another-topic" }.encrypt()
        noop.deliverMail(100, secondTopic)
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
    fun corruption() {
        // Check the enclave correctly rejects messages with corrupted headers or bodies.
        noop.start(null, null, null)
        val mail = buildMail(noop)
        val encrypted = mail.encrypt()
        for (i in encrypted.indices) {
            encrypted[i] = encrypted[i].inc()
            var e: Throwable = assertThrows<RuntimeException>("iteration $i") { noop.deliverMail(i.toLong(), encrypted) }
            while (e.cause != null) e = e.cause!!
            assertTrue(corruptionErrors.none { e.message!! in it }, "Unrecognised error: ${e.message!!}")
            encrypted[i] = encrypted[i].dec()
        }
    }

    @Test
    fun routingHint() {
        // Make a call into enclave1, which then requests sending a mail to a client with its routing hint set. Tests
        // posting mail from inside a local call using an EnclaveInstanceInfo.
        class Enclave1 : Enclave() {
            override fun receiveMail(id: EnclaveMailId, mail: EnclaveMail) {
                val outbound = createMail(mail.authenticatedSender!!, "hello".toByteArray())
                postMail(outbound, mail.from!!)
                acknowledgeMail(id)
            }
        }
        val host = MockHost.loadMock<Enclave1>()
        host.start(null, null, object : EnclaveHost.MailCallbacks {
            override fun postMail(encryptedBytes: ByteArray, routingHint: String?) {
                assertEquals("bob", routingHint!!)
                val message: EnclaveMail = Mail.decrypt(encryptedBytes, keyPair.private)
                assertEquals("hello", String(message.bodyAsBytes))
            }
        })
        val messageFromBob = buildMail(host)
        messageFromBob.from = "bob"
        host.deliverMail(1, messageFromBob.encrypt())
    }

    private fun buildMail(host: MockHost<*>, body: ByteArray = messageBytes): MutableMail {
        val mail = host.enclaveInstanceInfo.createMail(body)
        mail.topic = "topic-123"
        mail.sequenceNumber = 1
        mail.privateKey = keyPair.private
        return mail
    }

    class NoopEnclave : Enclave() {
        override fun receiveMail(id: EnclaveMailId, mail: EnclaveMail) {
        }
    }

    // Receives mail, decrypts it and gives the body back to the host.
    class MailEchoEnclave : Enclave() {
        override fun receiveMail(id: EnclaveMailId, mail: EnclaveMail) {
            val answer: ByteArray? = callUntrustedHost(writeData {
                writeIntLengthPrefixBytes(mail.bodyAsBytes)
                writeInt(id.toInt())
            })
            when (val str = answer?.let { String(it) }) {
                "acknowledge" -> acknowledgeMail(id)
                "an answer" -> return
                "post" -> postMail(createMail(Curve25519PublicKey(mail.bodyAsBytes), "sent to second enclave".toByteArray()), "routing hint")
                null -> return
                else -> throw IllegalStateException(str)
            }
        }
    }
}