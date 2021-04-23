package com.r3.conclave.host

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.EnclavePostOffice
import com.r3.conclave.enclave.internal.MockEnclaveEnvironment
import com.r3.conclave.internaltesting.throwableWithMailCorruptionErrorMessage
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.testing.MockHost
import com.r3.conclave.testing.internal.MockInternals
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.security.PrivateKey
import java.security.PublicKey

class MailHostTest {
    companion object {
        private val messageBytes = "message".toByteArray()
    }

    private val privateKey = Curve25519PrivateKey.random()
    private val echo by lazy { MockHost.loadMock<MailEchoEnclave>() }
    private val noop by lazy { MockHost.loadMock<NoopEnclave>() }
    private val postOffices = HashMap<Pair<EnclaveInstanceInfo, String>, PostOffice>()

    @AfterEach
    fun reset() {
        MockEnclaveEnvironment.platformReset()
    }

    @Test
    fun `encrypt and deliver mail`() {
        echo.start(null, null)
        val encryptedMail = buildMail(echo)
        var response: ByteArray? = null
        echo.deliverMail(1, encryptedMail, "test") { bytes ->
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
        echo.start(null, null)
        val encryptedMail = buildMail(echo)
        // In response to the delivered mail, the enclave sends us a local message, and we send a local message back.
        // It asserts the answer we give is as expected.
        echo.deliverMail(1, encryptedMail, "test") { "an answer".toByteArray() }
    }

    @Test
    fun `mail acknowledgement`() {
        var acknowledgementID: Long? = null
        echo.start(null) { commands ->
            acknowledgementID = (commands.single() as MailCommand.AcknowledgeMail).mailID
        }
        // First delivery doesn't acknowledge because we don't tell it to.
        echo.deliverMail(1, buildMail(echo), "test") { null }
        assertNull(acknowledgementID)
        echo.deliverMail(2, buildMail(echo), "test") { "acknowledge".toByteArray() }
        assertEquals(2, acknowledgementID!!)
    }

    @Test
    fun `sequence numbers`() {
        // Verify that the enclave rejects a replay of the same message, or out of order delivery.
        noop.start(null, null)
        val encrypted0 = buildMail(noop, sequenceNumber = 0, body = "message 0".toByteArray())
        val encrypted1 = buildMail(noop, sequenceNumber = 1, body = "message 1".toByteArray())
        val encrypted2 = buildMail(noop, sequenceNumber = 2, body = "message 2".toByteArray())
        val encrypted50 = buildMail(noop, sequenceNumber = 50, body = "message 50".toByteArray())
        // Deliver message 1.
        noop.deliverMail(100, encrypted0, "test")
        // Cannot deliver message 2 twice even with different IDs.
        noop.deliverMail(100, encrypted1, "test")
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(100, encrypted1, "test") }
            .withMessageContaining("Mail with sequence number 1 on topic topic-123 has already been seen, was expecting 2.")
        // Cannot now re-deliver message 1 because the sequence number would be going backwards.
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(100, encrypted0, "test") }
            .withMessageContaining("Mail with sequence number 0 on topic topic-123 has already been seen, was expecting 2.")
        // Can deliver message 3
        noop.deliverMail(101, encrypted2, "test")
        // Seq nums may not have gaps.
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(102, encrypted50, "test") }
            .withMessageContaining("Next sequence number on topic topic-123 should be 3 but is instead 50.")

        // Seq nums of different topics are independent
        val secondTopic = buildMail(noop, topic = "another-topic", sequenceNumber = 0)
        noop.deliverMail(100, secondTopic, "test")

        // Seq nums from different senders are independent
        val secondSender = buildMail(noop, senderPrivateKey = Curve25519PrivateKey.random(), sequenceNumber = 0)
        noop.deliverMail(100, secondSender, "test")
    }

    @Test
    fun `sequence numbers must start from zero`() {
        noop.start(null, null)
        val encrypted1 = buildMail(noop, sequenceNumber = 1)
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(100, encrypted1, "test") }
            .withMessageContaining("First time seeing mail with topic topic-123 so the sequence number must be zero but is instead 1.")
    }

    @Test
    fun corruption() {
        // Check the enclave correctly rejects messages with corrupted headers or bodies.
        noop.start(null, null)
        val encrypted = buildMail(noop)
        for (i in encrypted.indices) {
            encrypted[i]++
            assertThatThrownBy {
                noop.deliverMail(i.toLong(), encrypted, "test")
            }.`is`(throwableWithMailCorruptionErrorMessage)
            encrypted[i]--
        }
    }

    @Test
    fun routingHint() {
        // Make a call into enclave1, which then requests sending a mail to a client with its routing hint set. Tests
        // posting mail from inside a local call using an EnclaveInstanceInfo.
        class Enclave1 : Enclave() {
            override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
                val outbound = postOffice(mail).encryptMail("hello".toByteArray())
                postMail(outbound, routingHint!!)
                acknowledgeMail(id)
            }
        }

        val host = MockHost.loadMock<Enclave1>()
        var postCommand: MailCommand.PostMail? = null
        host.start(null) { commands ->
            postCommand = commands.filterIsInstance<MailCommand.PostMail>().single()
        }
        val messageFromBob = buildMail(host)
        host.deliverMail(1, messageFromBob, "test")
        assertThat(postCommand!!.routingHint).isEqualTo("test")
        val message: EnclaveMail = decryptMail(host, bytes = postCommand!!.encryptedBytes)
        assertEquals("hello", String(message.bodyAsBytes))
    }

    @Test
    fun `multiple commands`() {
        class Enclave1 : Enclave() {
            private var previousSender: PublicKey? = null
            override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
                previousSender = mail.authenticatedSender
                postMail(postOffice(mail).encryptMail("hello".toByteArray()), null)
                postMail(postOffice(mail).encryptMail("world".toByteArray()), null)
                acknowledgeMail(id)
            }

            override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
                postMail(postOffice(previousSender!!).encryptMail("123".toByteArray()), null)
                postMail(postOffice(previousSender!!).encryptMail("456".toByteArray()), null)
                return null
            }
        }

        val host = MockHost.loadMock<Enclave1>()
        var previousCommands: List<MailCommand>? = null
        host.start(null) { commands -> previousCommands = commands }

        val messageFromBob = buildMail(host)
        host.deliverMail(123, messageFromBob, "test")

        assertThat(previousCommands).hasSize(3)
        with(previousCommands!![0] as MailCommand.PostMail) {
            assertEquals("hello", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
        with(previousCommands!![1] as MailCommand.PostMail) {
            assertEquals("world", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
        assertThat(previousCommands!![2]).isEqualTo(MailCommand.AcknowledgeMail(123))

        // callEnclave should cause a separate set of commands to come through.
        host.callEnclave(byteArrayOf())
        assertThat(previousCommands).hasSize(2)
        with(previousCommands!![0] as MailCommand.PostMail) {
            assertEquals("123", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
        with(previousCommands!![1] as MailCommand.PostMail) {
            assertEquals("456", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
    }

    @ParameterizedTest
    @EnumSource
    fun `enclave can read mail targeted for older platform version`(context: CreateMailContext) {
        val enclave1 = MockHost.loadMock<CreateMailEnclave>()
        enclave1.start(null, null)
        val oldEncryptedMail = context.createMail("secret".toByteArray(), enclave1)
        enclave1.close()

        // Shutdown the enclave and "update" the platform so that we have a new CPUSVN. The new enclave's (default)
        // encryption key will be different from its old one, but we still expect the enclave to be able to decrypt it.
        MockEnclaveEnvironment.platformUpdate()

        val enclave2 = MockHost.loadMock<CreateMailEnclave>()
        enclave2.start(null, null)
        var decryptedByEnclave: String? = null
        enclave2.deliverMail(1, oldEncryptedMail, "test") { bytes ->
            decryptedByEnclave = String(bytes)
            null
        }

        assertThat(decryptedByEnclave).isEqualTo("terces")
    }

    @ParameterizedTest
    @EnumSource
    fun `enclave cannot read mail targeted for newer platform version`(context: CreateMailContext) {
        // Imagine the current platform version has a bug in it and so we update and the client creates mail from that.
        MockEnclaveEnvironment.platformUpdate()
        val enclave1 = MockHost.loadMock<CreateMailEnclave>()
        enclave1.start(null, null)
        val newEncryptedMail = context.createMail("secret".toByteArray(), enclave1)
        enclave1.close()

        // Let's revert the update and return the platform to its insecure version.
        MockEnclaveEnvironment.platformDowngrade()

        val enclave2 = MockHost.loadMock<CreateMailEnclave>()
        enclave2.start(null, null)
        assertThatThrownBy {
            enclave2.deliverMail(1, newEncryptedMail, null) { null }
        }.hasMessageContaining("SGX_ERROR_INVALID_CPUSVN")
    }

    @ParameterizedTest
    @EnumSource
    fun `enclave with higher revocation level can read older mail`(context: CreateMailContext) {
        val oldEnclave = MockInternals.createMock(CreateMailEnclave::class.java, isvProdId = 1, isvSvn = 1)
        oldEnclave.start(null, null)
        val oldEncryptedMail = context.createMail("secret!".toByteArray(), oldEnclave)
        oldEnclave.close()

        val newEnclave = MockInternals.createMock(CreateMailEnclave::class.java, isvProdId = 1, isvSvn = 2)
        newEnclave.start(null, null)
        var decryptedByEnclave: String? = null
        newEnclave.deliverMail(1, oldEncryptedMail, null) { bytes ->
            decryptedByEnclave = String(bytes)
            null
        }

        assertThat(decryptedByEnclave).isEqualTo("!terces")
    }

    @ParameterizedTest
    @EnumSource
    fun `enclave with lower revocation level cannot read newer mail`(context: CreateMailContext) {
        val newEnclave = MockInternals.createMock(CreateMailEnclave::class.java, isvProdId = 1, isvSvn = 2)
        newEnclave.start(null, null)
        val newEncryptedMail = context.createMail("secret!".toByteArray(), newEnclave)
        newEnclave.close()

        val oldEnclave = MockInternals.createMock(CreateMailEnclave::class.java, isvProdId = 1, isvSvn = 1)
        oldEnclave.start(null, null)
        assertThatThrownBy {
            oldEnclave.deliverMail(1, newEncryptedMail, null) { null }
        }.hasMessageContaining("SGX_ERROR_INVALID_ISVSVN")
    }

    @ParameterizedTest
    @ValueSource(strings = ["destination+topic", "destination", "mail", "topic+eii", "eii"])
    fun `postOffice() methods return cached instances`(overload: String) {
        class PostOfficeEnclave : Enclave() {
            override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
                val receivedOverload = String(mail.bodyAsBytes)
                val instances = HashSet<EnclavePostOffice>()
                repeat(2) {
                    instances += when (receivedOverload) {
                        "destination+topic" -> postOffice(mail.authenticatedSender, "topic")
                        "destination" -> postOffice(mail.authenticatedSender)
                        "mail" -> postOffice(mail)
                        "topic+eii" -> postOffice(enclaveInstanceInfo, "topic")
                        "eii" -> postOffice(enclaveInstanceInfo)
                        else -> throw IllegalArgumentException(receivedOverload)
                    }
                }

                assertThat(instances).hasSize(1)
                val topic = instances.first().topic
                val destinationPublicKey = instances.first().destinationPublicKey

                when (receivedOverload) {
                    "destination+topic", "topic+eii" -> assertThat(topic).isEqualTo("topic")
                    "destination", "eii" -> assertThat(topic).isEqualTo("default")
                    "mail" -> assertThat(topic).isEqualTo(mail.topic)
                    else -> throw IllegalArgumentException(receivedOverload)
                }

                when (receivedOverload) {
                    "destination+topic", "destination", "mail" -> assertThat(destinationPublicKey).isEqualTo(mail.authenticatedSender)
                    "topic+eii", "eii" -> assertThat(destinationPublicKey).isEqualTo(enclaveInstanceInfo.encryptionKey)
                    else -> throw IllegalArgumentException(receivedOverload)
                }
            }
        }

        val host = MockHost.loadMock<PostOfficeEnclave>()
        host.start(null, null)
        host.deliverMail(1, buildMail(host, body = overload.toByteArray()), null)
    }

    @Test
    fun `not possible to create mail to an enclave using just its encryption key`() {
        noop.start(null, null)
        val mailBytes = PostOffice.create(noop.enclaveInstanceInfo.encryptionKey).encryptMail("message".toByteArray())
        assertThatIllegalArgumentException().isThrownBy {
            noop.deliverMail(1, mailBytes, null)
        }.withMessageContaining("Make sure EnclaveInstanceInfo.createPostOffice is used.")
    }

    private fun buildMail(
        host: MockHost<*>,
        topic: String = "topic-123",
        sequenceNumber: Long? = null,
        senderPrivateKey: PrivateKey = privateKey,
        body: ByteArray = messageBytes
    ): ByteArray {
        val postOffice = if (sequenceNumber != null) {
            host.enclaveInstanceInfo.createPostOffice(senderPrivateKey, topic).setNextSequenceNumber(sequenceNumber)
        } else {
            postOffices.computeIfAbsent(Pair(host.enclaveInstanceInfo, topic)) {
                host.enclaveInstanceInfo.createPostOffice(senderPrivateKey, topic)
            }
        }
        return postOffice.encryptMail(body)
    }

    private fun decryptMail(host: EnclaveHost, topic: String = "topic-123", bytes: ByteArray): EnclaveMail {
        return postOffices.getValue(Pair(host.enclaveInstanceInfo, topic)).decryptMail(bytes)
    }

    class NoopEnclave : Enclave() {
        override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
        }
    }

    // Receives mail, decrypts it and gives the body back to the host.
    class MailEchoEnclave : Enclave() {
        override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
            val answer: ByteArray? = callUntrustedHost(writeData {
                writeIntLengthPrefixBytes(mail.bodyAsBytes)
                writeInt(id.toInt())
            })
            when (val str = answer?.let { String(it) }) {
                "acknowledge" -> acknowledgeMail(id)
                "an answer" -> return
                "post" -> postMail(
                    postOffice(Curve25519PublicKey(mail.bodyAsBytes)).encryptMail("sent to second enclave".toByteArray()),
                    routingHint
                )
                null -> return
                else -> throw IllegalStateException(str)
            }
        }
    }

    class CreateMailEnclave : Enclave() {
        // Encrypt
        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
            return postOffice(enclaveInstanceInfo).encryptMail(bytes.reversedArray())
        }

        // Decrypt
        override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
            callUntrustedHost(mail.bodyAsBytes)
        }
    }

    enum class CreateMailContext {
        INSIDE_ENCLAVE {
            override fun createMail(body: ByteArray, host: EnclaveHost): ByteArray {
                // Assumes the enclave is CreateMailEnclave
                return host.callEnclave(body)!!
            }
        },
        OUTSIDE_ENCLAVE {
            override fun createMail(body: ByteArray, host: EnclaveHost): ByteArray {
                return host.enclaveInstanceInfo.createPostOffice().encryptMail(body.reversedArray())
            }
        };

        abstract fun createMail(body: ByteArray, host: EnclaveHost): ByteArray
    }
}
