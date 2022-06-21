package com.r3.conclave.host

import com.r3.conclave.client.PostOfficeBuilder
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveStartException
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.EnclavePostOffice
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.host.kds.KDSConfiguration
import com.r3.conclave.internaltesting.kds.MockKDS
import com.r3.conclave.mail.*
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.AEADBadTagException

class EnclaveMailMockTest {
    companion object {
        private val messageBytes = "message".toByteArray()

        // Get the set of persistent map and multithreading enablement options for general tests. The case
        // where the enclave is multithreaded AND the persistent map is excluded, as this is an invalid state.
        @JvmStatic
        fun validPersistentMapEnablementStates(): List<Arguments> {
            return listOf(
                Arguments.arguments(false, false),
                Arguments.arguments(false, true),
                Arguments.arguments(true, false)
            )
        }
    }

    private val mockConfiguration = MockConfiguration()
    private val privateKey = Curve25519PrivateKey.random()
    private val echo by lazy { createMockHost(MailEchoEnclave::class.java, mockConfiguration) }
    private val noop by lazy { createMockHost(NoopEnclave::class.java, mockConfiguration) }
    private val postOffices = HashMap<Pair<EnclaveInstanceInfo, String>, PostOffice>()

    @RegisterExtension
    private val mockKds = MockKDS()
    private val enclaveKdsConfig = EnclaveKdsConfig(
        kdsEnclaveConstraint = mockKds.enclaveConstraint,
        persistenceKeySpec = null
    )
    private val hostKdsConfig = KDSConfiguration(mockKds.url.toString())

    @Test
    fun `deliverMail before start`() {
        assertThatIllegalStateException().isThrownBy {
            noop.deliverMail(byteArrayOf(), null)
        }.withMessage("The enclave host has not been started.")
        assertThatIllegalStateException().isThrownBy {
            noop.deliverMail(byteArrayOf(), null) { it }
        }.withMessage("The enclave host has not been started.")
    }

    @Test
    fun `encrypt and deliver mail`() {
        echo.start(null, null, null) { }
        val encryptedMail = buildMail(echo)
        var response: ByteArray? = null
        echo.deliverMail(encryptedMail, "test") { bytes ->
            response = bytes
            null  // No response back to enclave.
        }
        response!!.deserialise {
            assertArrayEquals(messageBytes, readIntLengthPrefixBytes())
        }
    }

    @Test
    fun `deliver mail and answer enclave`() {
        echo.start(null, null, null) { }
        val encryptedMail = buildMail(echo)
        // In response to the delivered mail, the enclave sends us a local message, and we send a local message back.
        // It asserts the answer we give is as expected.
        echo.deliverMail(encryptedMail, "test") { "an answer".toByteArray() }
    }

    @Test
    fun `deliverMail cannot be called in a callback to another deliverMail when persistent map is enabled`() {
        mockConfiguration.enablePersistentMap = true

        echo.start(null, null, null) { }

        var thrown: IllegalStateException? = null
        echo.deliverMail(buildMail(echo), "routingHint") {
            thrown = assertThrows {
                echo.deliverMail(buildMail(echo), "routingHint") { null }
            }
            null
        }
        assertThat(thrown!!.message).isEqualTo(
            "deliverMail cannot be called in a callback to another deliverMail when the persistent map is enabled."
        )
    }

    @Test
    fun `detect missing call to postMail when persistent map is enabled`() {
        class MissingPostEnclave : Enclave() {
            override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
                postOffice(mail).encryptMail("missing!".toByteArray())
            }
            override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
                postOffice(Curve25519PublicKey(bytes)).encryptMail("missing!".toByteArray())
                return null
            }
        }

        mockConfiguration.enablePersistentMap = true
        val missingPost = createMockHost(MissingPostEnclave::class.java, mockConfiguration)
        missingPost.start(null, null, null) { }

        assertThatIllegalStateException().isThrownBy {
            missingPost.deliverMail(buildMail(missingPost), null)
        }.withMessage("There were 1 mail(s) created which were not posted with postMail.")

        assertThatIllegalStateException().isThrownBy {
            missingPost.callEnclave(privateKey.publicKey.encoded)
        }.withMessage("There were 1 mail(s) created which were not posted with postMail.")
    }

    @Test
    fun `exception thrown by receiveMail does not impact subsequent call`() {
        echo.start(null, null, null) { }

        assertThatIllegalStateException().isThrownBy {
            echo.deliverMail(buildMail(echo), null) { "throw".toByteArray() }
        }.withMessage("throw")

        var response: ByteArray? = null
        echo.deliverMail(buildMail(echo), "test") { bytes ->
            response = bytes
            null
        }
        response!!.deserialise {
            assertArrayEquals(messageBytes, readIntLengthPrefixBytes())
        }
    }

    @Test
    fun `sequence numbers`() {
        // Verify that the enclave rejects a replay of the same message, or out of order delivery.
        noop.start(null, null, null) { }
        val encrypted0 = buildMail(noop, sequenceNumber = 0, body = "message 0".toByteArray())
        val encrypted1 = buildMail(noop, sequenceNumber = 1, body = "message 1".toByteArray())
        val encrypted2 = buildMail(noop, sequenceNumber = 2, body = "message 2".toByteArray())
        val encrypted50 = buildMail(noop, sequenceNumber = 50, body = "message 50".toByteArray())
        // Deliver message 1.
        noop.deliverMail(encrypted0, "test")
        // Cannot deliver message 2 twice even with different IDs.
        noop.deliverMail(encrypted1, "test")
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(encrypted1, "test") }
            .withMessageContaining("Mail with sequence number 1 on topic topic-123 has already been seen, was expecting 2 instead.")
        // Cannot now re-deliver message 1 because the sequence number would be going backwards.
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(encrypted0, "test") }
            .withMessageContaining("Mail with sequence number 0 on topic topic-123 has already been seen, was expecting 2 instead.")
        // Can deliver message 3
        noop.deliverMail(encrypted2, "test")
        // Seq nums may not have gaps.
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(encrypted50, "test") }
            .withMessageContaining("Next sequence number on topic topic-123 should be 3 but is instead 50.")

        // Seq nums of different topics are independent
        val secondTopic = buildMail(noop, topic = "another-topic", sequenceNumber = 0)
        noop.deliverMail(secondTopic, "test")

        // Seq nums from different senders are independent
        val secondSender = buildMail(noop, senderPrivateKey = Curve25519PrivateKey.random(), sequenceNumber = 0)
        noop.deliverMail(secondSender, "test")
    }

    @Test
    fun `sequence numbers must start from zero`() {
        noop.start(null, null, null) { }
        val encrypted1 = buildMail(noop, sequenceNumber = 1)
        assertThatIllegalStateException()
            .isThrownBy { noop.deliverMail(encrypted1, "test") }
            .withMessageContaining("First time seeing mail with topic topic-123 so the sequence number must be zero but is instead 1.")
    }

    @Test
    fun `sequence numbers are ignored for KDS mail`() {
        val kdsPostOffice = createKdsPostOffice()

        val enclaveHost = createMockHost(NoopEnclave::class.java, null, enclaveKdsConfig)
        enclaveHost.start(null, null, null, hostKdsConfig) { }

        kdsPostOffice.encryptMail(byteArrayOf())
        val secondKdsMail = kdsPostOffice.encryptMail(byteArrayOf())
        kdsPostOffice.encryptMail(byteArrayOf())
        val fourthKdsMail = kdsPostOffice.encryptMail(byteArrayOf())
        assertThat(getMailSequenceNumber(secondKdsMail))
            .isNotZero
            .isNotEqualTo(getMailSequenceNumber(fourthKdsMail))

        assertDoesNotThrow {
            // Ensure the enclaveHost does not throw because it did not receive mail with sequence number 0
            // This test covers the case when the Enclave requests the key from the kds service
            enclaveHost.deliverMail(secondKdsMail, null)
        }

        assertDoesNotThrow {
            // Ensure the enclaveHost does not throw because it did not receive mail with sequence number 2
            // This test covers the case when Enclave uses its cache to retrieve the key
            enclaveHost.deliverMail(fourthKdsMail, null)
        }
    }

    @Test
    fun `different KDS keys can be used for the same topic and sender`() {
        class EchoEnclave : Enclave() {
            override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
                postMail(postOffice(mail).encryptMail(mail.bodyAsBytes), routingHint)
            }
        }

        val senderPrivateKey = Curve25519PrivateKey.random()
        val postOffice1 = createKdsPostOffice(keyName = "name1", senderPrivateKey)
        val postOffice2 = createKdsPostOffice(keyName = "name2", senderPrivateKey)

        // Make sure we're dealing with different KDS keys
        assertThat(postOffice1.destinationPublicKey).isNotEqualTo(postOffice2.destinationPublicKey)
        // But using the same (topic + sender) pair
        assertThat(postOffice1.topic).isEqualTo(postOffice1.topic)
        assertThat(postOffice1.senderPublicKey).isEqualTo(postOffice1.senderPublicKey)

        var postedMail: ByteArray? = null

        val enclaveHost = createMockHost(EchoEnclave::class.java, null, enclaveKdsConfig)
        enclaveHost.start(null, null, null, hostKdsConfig) {
            postedMail = it.filterIsInstance<MailCommand.PostMail>().single().encryptedBytes
        }

        enclaveHost.deliverMail(postOffice1.encryptMail("1".toByteArray()), null)
        assertThat(postOffice1.decryptMail(postedMail!!).bodyAsBytes).isEqualTo("1".toByteArray())

        enclaveHost.deliverMail(postOffice2.encryptMail("2".toByteArray()), null)
        assertThat(postOffice2.decryptMail(postedMail!!).bodyAsBytes).isEqualTo("2".toByteArray())
    }

    @Test
    fun `check that EnclavePostOffice senderPrivateKey is correct by verifying its related public key`() {
        class PublicKeyEnclave : Enclave() {
            override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
                val privateKey = postOffice(mail).senderPrivateKey as Curve25519PrivateKey
                val publicKey = privateKey.publicKey
                callUntrustedHost(publicKey.encoded)
            }
        }
        val enclaveHost = createMockHost(PublicKeyEnclave::class.java, null, enclaveKdsConfig)
        enclaveHost.start(null, null, null, hostKdsConfig) {}

        val hostKdsPostOffice = createKdsPostOffice(keyName = "name1", Curve25519PrivateKey.random())
        val mail = hostKdsPostOffice.encryptMail("Dummy message".toByteArray())

        var publicKeyFromEnclave: ByteArray? = null
        enclaveHost.deliverMail(mail, null) { buffer ->
            publicKeyFromEnclave = buffer
            null
        }
        assertThat(publicKeyFromEnclave!!).isEqualTo(hostKdsPostOffice.destinationPublicKey.encoded)
    }

    private fun getMailSequenceNumber(mailBytes: ByteArray): Long {
        return Mail.getUnauthenticatedHeader(mailBytes).sequenceNumber
    }

    private fun createKdsPostOffice(
        keyName: String = "name",
        senderPrivateKey: PrivateKey = Curve25519PrivateKey.random()
    ): PostOffice {
        val keySpec = KDSKeySpec(
            keyName,
            MasterKeyType.DEBUG,
            "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE"
        )
        return PostOfficeBuilder
            .usingKDS(mockKds.url, keySpec, enclaveKdsConfig.kdsEnclaveConstraint)
            .setSenderPrivateKey(senderPrivateKey)
            .build()
    }

    @Test
    fun corruption() {
        // Check the enclave correctly rejects messages with corrupted headers or bodies.
        noop.start(null, null, null) { }
        val encrypted = buildMail(noop)
        for (i in encrypted.indices) {
            encrypted[i]++
            assertThatThrownBy {
                noop.deliverMail(encrypted, "test")
            }.isInstanceOf(MailDecryptionException::class.java)
            encrypted[i]--
        }
    }

    @Test
    fun routingHint() {
        // Make a call into enclave1, which then requests sending a mail to a client with its routing hint set. Tests
        // posting mail from inside a local call using an EnclaveInstanceInfo.
        class Enclave1 : Enclave() {
            override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
                val outbound = postOffice(mail).encryptMail("hello".toByteArray())
                postMail(outbound, routingHint!!)
            }
        }

        val host = createMockHost(Enclave1::class.java)
        var postCommand: MailCommand.PostMail? = null
        host.start(null, null, null) { commands ->
            postCommand = commands.filterIsInstance<MailCommand.PostMail>().single()
        }
        val messageFromBob = buildMail(host)
        host.deliverMail(messageFromBob, "test")
        assertThat(postCommand!!.routingHint).isEqualTo("test")
        val message: EnclaveMail = decryptMail(host, bytes = postCommand!!.encryptedBytes)
        assertEquals("hello", String(message.bodyAsBytes))
    }

    @Test
    fun `multi threaded enclaves should throw an error when started if the persistent map is enabled`() {
        class ThreadSafeNoopEnclave : NoopEnclave() {
            override val threadSafe: Boolean get() = true
        }
        mockConfiguration.enablePersistentMap = true
        val host = createMockHost(ThreadSafeNoopEnclave::class.java, mockConfiguration)
        val e = assertThrows<EnclaveLoadException> {
            host.start(null, null, null) {}
        }
        assertThat(e.cause!!).isInstanceOf(EnclaveStartException::class.java)
        assertThat(e.cause!!).hasMessageStartingWith("The persistent map is not available in multi-threaded enclaves.")
    }

    @ParameterizedTest
    @MethodSource("validPersistentMapEnablementStates")
    fun `multiple commands`(threadSafeEnclave: Boolean, enablePersistentMap: Boolean) {
        val enclaveClass = if (threadSafeEnclave) {
            class ThreadSafeMultipleCommandsEnclave : MultipleCommandsEnclave() {
                override val threadSafe: Boolean get() = true
            }
            ThreadSafeMultipleCommandsEnclave::class.java
        } else {
            class NonThreadSafeMultipleCommandsEnclave : MultipleCommandsEnclave() {
                override val threadSafe: Boolean get() = false
            }
            NonThreadSafeMultipleCommandsEnclave::class.java
        }

        val mockConfiguration = MockConfiguration()
        mockConfiguration.enablePersistentMap = enablePersistentMap
        val host = createMockHost(enclaveClass, mockConfiguration)

        var capturedCommands: List<MailCommand> = emptyList()
        host.start(null, null, null) { commands -> capturedCommands = commands }

        val messageFromBob = buildMail(host)
        host.deliverMail(messageFromBob, "test")

        // If the enclaves persistent map is not enabled, then no sealed state should be emitted
        assertThat(capturedCommands).hasSize(if (enablePersistentMap) 3 else 2)
        with(capturedCommands[0] as MailCommand.PostMail) {
            assertEquals("hello", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
        with(capturedCommands[1] as MailCommand.PostMail) {
            assertEquals("world", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
        if (enablePersistentMap) {
            assertThat(capturedCommands[2]).isInstanceOf(MailCommand.StoreSealedState::class.java)
        }

        capturedCommands = emptyList()
        // callEnclave should cause a separate set of commands to come through.
        host.callEnclave(byteArrayOf())

        assertThat(capturedCommands).hasSize(if (enablePersistentMap) 3 else 2)
        with(capturedCommands[0] as MailCommand.PostMail) {
            assertEquals("123", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
        with(capturedCommands[1] as MailCommand.PostMail) {
            assertEquals("456", String(decryptMail(host, bytes = encryptedBytes).bodyAsBytes))
        }
        if (enablePersistentMap) {
            assertThat(capturedCommands[2]).isInstanceOf(MailCommand.StoreSealedState::class.java)
        }
    }

    @Test
    fun `enclave has different encryption key on restart and can't decrypt mail for previous instance`() {
        noop.start(null, null, null) { }
        val previousEncryptionKey = noop.enclaveInstanceInfo.encryptionKey
        val encryptedMail = buildMail(noop)
        noop.close()

        val noop2 = createMockHost(NoopEnclave::class.java)
        noop2.start(null, null, null) { }

        assertThat(previousEncryptionKey).isNotEqualTo(noop2.enclaveInstanceInfo.encryptionKey)
        assertThatThrownBy {
            noop2.deliverMail(encryptedMail, null)
        }.hasRootCauseInstanceOf(AEADBadTagException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["destination+topic", "destination", "mail", "topic+eii", "eii"])
    fun `postOffice() methods return cached instances`(overload: String) {
        class PostOfficeEnclave : Enclave() {
            override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
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

        val host = createMockHost(PostOfficeEnclave::class.java)
        host.start(null, null, null) { }
        host.deliverMail(buildMail(host, body = overload.toByteArray()), null)
    }

    private fun buildMail(
        host: EnclaveHost,
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

    open class NoopEnclave : Enclave() {
        override fun receiveMail(mail: EnclaveMail, routingHint: String?) = Unit
    }

    // Receives mail, decrypts it and gives the body back to the host.
    class MailEchoEnclave : Enclave() {
        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            val answer: ByteArray? = callUntrustedHost(writeData {
                writeIntLengthPrefixBytes(mail.bodyAsBytes)
            })
            when (val str = answer?.let { String(it) }) {
                "an answer" -> return
                "post" -> postMail(
                    postOffice(Curve25519PublicKey(mail.bodyAsBytes)).encryptMail("sent to second enclave".toByteArray()),
                    routingHint
                )
                null -> return
                else -> throw IllegalStateException(str)
            }
        }

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? = callUntrustedHost(bytes)
    }

    abstract class MultipleCommandsEnclave : Enclave() {
        abstract override val threadSafe: Boolean

        private var previousSender: PublicKey? = null

        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            previousSender = mail.authenticatedSender
            postMail(postOffice(mail).encryptMail("hello".toByteArray()), null)
            postMail(postOffice(mail).encryptMail("world".toByteArray()), null)
        }

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
            postMail(postOffice(previousSender!!).encryptMail("123".toByteArray()), null)
            postMail(postOffice(previousSender!!).encryptMail("456".toByteArray()), null)
            return null
        }
    }
}
