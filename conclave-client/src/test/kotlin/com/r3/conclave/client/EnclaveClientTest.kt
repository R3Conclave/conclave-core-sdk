package com.r3.conclave.client

import com.r3.conclave.common.*
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.internaltesting.MockEnclaveTransport
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.utilities.internal.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class EnclaveClientTest {
    private var _enclaveTransport: MockEnclaveTransport? = null
    private var testingAsyncMail = false

    private val mockConfiguration = MockConfiguration()

    @AfterEach
    fun close() {
        _enclaveTransport?.close()
    }

    private val enclaveTransport: MockEnclaveTransport get() {
        return _enclaveTransport ?: run {
            val enclaveClass = if (testingAsyncMail) {
                mockConfiguration.enablePersistentMap = true
                DelayedEchoEnclave::class
            } else {
                EchoEnclave::class
            }
            MockEnclaveTransport(enclaveClass, mockConfiguration).also {
                it.startEnclave()
                _enclaveTransport = it
            }
        }
    }

    @Test
    fun `the constraints are checked on start`() {
        enclaveTransport.startEnclave()
        val client = EnclaveClient(enclaveConstraint(codeHash = randomHash()))
        assertThatThrownBy {
            client.start(enclaveTransport)
        }.isInstanceOf(InvalidEnclaveException::class.java)
    }

    @Test
    fun `send mail with response`() {
        enclaveTransport.startEnclave()
        val client = enclaveTransport.startNewClient()
        val response = client.sendMail("Hello".toByteArray())
        assertThat(response?.bodyAsBytes?.let(::String)).isEqualTo("Hello")
    }

    @Test
    fun `send mail with no response`() {
        testingAsyncMail = true
        enclaveTransport.startEnclave()
        val client = enclaveTransport.startNewClient()
        val response = client.sendMail("Hello".toByteArray())
        assertThat(response).isNull()
    }

    @Test
    fun `send mail after enclave restart`() {
        enclaveTransport.startEnclave()
        val client = enclaveTransport.startNewClient()
        val previousEnclaveKey = enclaveTransport.enclaveHost.enclaveInstanceInfo.encryptionKey
        enclaveTransport.restartEnclave()
        assertThat(enclaveTransport.enclaveHost.enclaveInstanceInfo.encryptionKey).isNotEqualTo(previousEnclaveKey)
        val response = client.sendMail("Hello".toByteArray())
        assertThat(response?.bodyAsBytes?.let(::String)).isEqualTo("Hello")
    }

    @Test
    fun `restart notification`() {
        enclaveTransport.startEnclave()
        var restartDetected = false
        val client = object : EnclaveClient(enclaveConstraint()) {
            override fun onEnclaveRestarted() {
                restartDetected = true
            }
        }
        client.start(enclaveTransport)
        enclaveTransport.restartEnclave()
        client.sendMail("Hello".toByteArray())
        assertThat(restartDetected).isTrue
    }

    @Test
    fun `constraint no longer satisified after enclave restart`() {
        enclaveTransport.startEnclave()
        val client = enclaveTransport.startNewClient()
        enclaveTransport.restartEnclave(configuration = MockConfiguration().apply { codeHash = randomHash() })
        assertThatIOException().isThrownBy {
            client.sendMail("Hello".toByteArray())
        }.withMessage("The enclave has a new EnclaveInstanceInfo which no longer satisfies the client's constraints")
    }

    @Test
    fun `polling for mail returns asychronous mail response`() {
        testingAsyncMail = true
        enclaveTransport.startEnclave()
        val client1 = enclaveTransport.startNewClient()
        val client2 = enclaveTransport.startNewClient()

        // First value is stored for client1
        client1.sendMail("Hello".toByteArray())
        assertThat(client1.pollMail()).isNull()

        // Sending another mail will cause the previous "Hello" to be posted to client1
        client2.sendMail("World".toByteArray())
        assertThat(client1.pollMail()?.let { String(it.bodyAsBytes) }).isEqualTo("Hello")
        assertThat(client2.pollMail()).isNull()

        // Finally client2 gets its submission.
        client1.sendMail("Hello".toByteArray())
        assertThat(client2.pollMail()?.let { String(it.bodyAsBytes) }).isEqualTo("World")
    }

    @Test
    fun `rollback detected by default with the exception containing the received mail, and client can continue to receive mail`() {
        mockConfiguration.enablePersistentMap = true
        enclaveTransport.startEnclave()
        val client = enclaveTransport.startNewClient()
        client.sendMail("Hello".toByteArray())
        enclaveTransport.restartEnclave(rollBackNumberOfStates = 1)
        val exception = assertThrows<EnclaveRollbackException> { client.sendMail("Hello?".toByteArray()) }
        assertThat(exception.mail?.bodyAsBytes?.let(::String)).isEqualTo("Hello?")

        val response = client.sendMail("Hello".toByteArray())
        assertThat(response?.bodyAsBytes?.let(::String)).isEqualTo("Hello")
    }

    @Test
    fun `rollback can be ignored`() {
        mockConfiguration.enablePersistentMap = true
        enclaveTransport.startEnclave()
        val client = object : EnclaveClient(enclaveConstraint()) {
            override fun ignoreEnclaveRollback(): Boolean = true
        }
        client.start(enclaveTransport)
        client.sendMail("Hello".toByteArray())
        enclaveTransport.restartEnclave(rollBackNumberOfStates = 1)
        val response = client.sendMail("Hello".toByteArray())
        assertThat(response?.bodyAsBytes?.let(::String)).isEqualTo("Hello")
    }

    @ParameterizedTest
    @CsvSource(value = [
        "false, false",
        "false, true",
        "true, false",
        "true, true"
    ])
    fun `client state restored without sending mail`(startClient: Boolean, closeClient: Boolean) {
        enclaveTransport.startEnclave()

        val constraint = enclaveConstraint()
        val privateKey = Curve25519PrivateKey.random()
        val client = EnclaveClient(privateKey, constraint)
        if (startClient) {
            client.start(enclaveTransport)
        }
        if (closeClient) {
            client.close()
        }

        val restored = EnclaveClient(client.save())
        assertThat(restored.enclaveConstraint).isEqualTo(constraint)
        assertThat(restored.clientPrivateKey).isEqualTo(privateKey)
        assertThat(restored.postOffices).isEmpty()
        assertThat(restored.lastSeenStateId).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun `client state restored after sending mail`(roundTripCount: Int) {
        mockConfiguration.enablePersistentMap = true
        enclaveTransport.startEnclave()
        val client = enclaveTransport.startNewClient()
        client.sendMail("topic1", "Hello".toByteArray(), null)
        client.sendMail("topic1", "Hello".toByteArray(), null)
        client.sendMail("topic2", "Hello".toByteArray(), null)
        assertThat(client.lastSeenStateId).isNotNull

        var restored = client
        repeat(roundTripCount) {
            restored = EnclaveClient(restored.save())
        }
        restored.start(enclaveTransport)

        assertThat(restored.postOffices).hasSize(2)
        assertThat(restored.postOffice("topic1").nextSequenceNumber).isEqualTo(2)
        assertThat(restored.postOffice("topic2").nextSequenceNumber).isEqualTo(1)
        assertThat(restored.lastSeenStateId).isEqualTo(client.lastSeenStateId)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `client can resume from previous state`(restartEnclave: Boolean) {
        testingAsyncMail = true
        enclaveTransport.startEnclave()

        val client = enclaveTransport.startNewClient()
        client.sendMail("Hello".toByteArray())

        val clientState = client.save()

        if (restartEnclave) {
            enclaveTransport.restartEnclave()
        }

        val restoredClient = EnclaveClient(clientState)
        restoredClient.start(enclaveTransport)
        val response = restoredClient.sendMail("World".toByteArray())
        assertThat(response?.let { String(it.bodyAsBytes) }).isEqualTo("Hello")
    }

    private fun enclaveConstraint(
        codeHash: SecureHash = enclaveTransport.enclaveHost.enclaveInstanceInfo.enclaveInfo.codeHash
    ): EnclaveConstraint {
        return EnclaveConstraint().apply {
            acceptableCodeHashes += codeHash
            minSecurityLevel = EnclaveSecurityInfo.Summary.INSECURE
        }
    }

    private fun randomHash(): SHA256Hash = SHA256Hash.wrap(ByteArray(32).also(Noise::random))

    class EchoEnclave : Enclave() {
        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            postMail(postOffice(mail).encryptMail(mail.bodyAsBytes), routingHint)
        }
    }

    class DelayedEchoEnclave : Enclave() {
        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            val previousState = persistentMap.put("state", writeData {
                writeIntLengthPrefixBytes(mail.bodyAsBytes)
                writeIntLengthPrefixBytes(mail.authenticatedSender.encoded)
                nullableWrite(routingHint) { writeUTF(it) }
            })

            previousState?.deserialise {
                val previousValue = readIntLengthPrefixBytes()
                val previousSender = Curve25519PublicKey(readIntLengthPrefixBytes())
                val previousRoutingHint = nullableRead { readUTF() }
                postMail(postOffice(previousSender).encryptMail(previousValue), previousRoutingHint)
            }
        }
    }
}
