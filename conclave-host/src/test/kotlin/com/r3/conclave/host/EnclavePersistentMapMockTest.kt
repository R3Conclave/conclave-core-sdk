package com.r3.conclave.host

import com.google.common.collect.HashBiMap
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.EnclavePostOffice
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.Get
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.WriteAction
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.WriteAction.Put
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.WriteAction.Remove
import com.r3.conclave.host.EnclavePersistentMapMockTest.RestartStrategy.*
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.mail.PostOffice
import com.r3.conclave.utilities.internal.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.PublicKey
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.IllegalStateException
import kotlin.reflect.KClass

class EnclavePersistentMapMockTest {
    private var useEchoEnclave = false
    private var threadSafeEnclave = false
    private var _host: MockHost? = null
    private var _client: MockClient? = null
    private var _mockConfiguration: MockConfiguration? = null

    @AfterEach
    fun cleanUp() {
        _host?.close()
    }

    private val mockConfiguration: MockConfiguration get() {
        return _mockConfiguration ?: run {
            val config = MockConfiguration()
            config.enablePersistentMap = true
            config.also { _mockConfiguration = it }
        }
    }

    private val host: MockHost get() {
        return _host ?: run {
            val enclaveClass = if (useEchoEnclave) {
                if (threadSafeEnclave) ThreadSafeEchoEnclave::class
                else NonThreadSafeEchoEnclave::class
            } else {
                if (threadSafeEnclave) ThreadSafePersistingEnclave::class
                else NonThreadSafePersistingEnclave::class
            }
            MockHost(enclaveClass, mockConfiguration).also { _host = it }
        }
    }

    private val client: MockClient get() = _client ?: MockClient(host).also { _client = it }

    @Test
    fun `new entry`() {
        val value = restartAfterWrite(Put("key", "please persist this"), Get("key"))
        assertThat(value).isEqualTo("please persist this")
    }

    @ParameterizedTest
    @EnumSource(RestartStrategy::class)
    fun `existing entry modified`(restartStrategy: RestartStrategy) {
        val value = processActions(
            restartStrategy,
            listOf(
                Put("key", "please persist this"),
                Put("key", "please persist this again")
            ),
            Get("key")
        )
        assertThat(value).isEqualTo("please persist this again")
    }

    @ParameterizedTest
    @EnumSource(RestartStrategy::class)
    fun `new entry deleted`(restartStrategy: RestartStrategy) {
        val value = processActions(
            restartStrategy,
            listOf(Put("key", "transient"), Remove("key")),
            Get("key")
        )
        assertThat(value).isNull()
    }

    @ParameterizedTest
    @EnumSource(RestartStrategy::class)
    fun `modified entry deleted`(restartStrategy: RestartStrategy) {
        val value = processActions(
            restartStrategy,
            listOf(Put("key", "new"), Put("key", "no-op"), Remove("key")),
            Get("key")
        )
        assertThat(value).isNull()
    }

    @Test
    fun `clients do not receive sealed state IDs when the persistent map is not enabled`() {
        useEchoEnclave = true
        mockConfiguration.enablePersistentMap = false

        val outbound = ByteArray(16)
        Random().nextBytes(outbound)

        client.sendMail(outbound)
        assertThat(client.receivedMail.size).isEqualTo(1)
        assertThat(client.receivedMail.last().bodyAsBytes).isEqualTo(outbound)
        assertThat(client.postOffice.lastSeenStateId).isNull()

        // Restart the enclave and send another mail item
        host.restartEnclave()
        Random().nextBytes(outbound)

        client.sendMail(outbound)
        assertThat(client.receivedMail.size).isEqualTo(2)
        assertThat(client.receivedMail.last().bodyAsBytes).isEqualTo(outbound)
        assertThat(client.postOffice.lastSeenStateId).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3])
    fun `host unable to roll back state if client has received explicit response`(rollBackState: Int) {
        client.sendSingleAction(Put("key", "v1"))
        client.sendSingleAction(Put("key", "v2"))
        client.sendSingleAction(Get("key"))
        host.restartEnclave(rollBackNumberOfStates = rollBackState)
        assertThatThrownBy {
            client.sendSingleAction(Get("key"))
        }.hasMessageContaining("Possible dropped mail or state roll back by the host detected.")
    }

    // TODO Requires mail acks: https://r3-cev.atlassian.net/browse/CON-616
    @Disabled
    @Test
    fun `host unable to roll back state even if client hasn't received explicit response`() {
        client.sendSingleAction(Put("key", "v1"))
        client.sendSingleAction(Put("key", "v2"))
        host.restartEnclave(rollBackNumberOfStates = 1)
        assertThatThrownBy {
            client.sendSingleAction(Get("key"))
        }.hasMessageContaining("Possible dropped mail or state roll back by the host detected.")
    }

    @Test
    fun `changes to the persistent map made by receiveFromUntrustedHost are preserved`() {
        executeActionsLocally(listOf(Put("key", "value")))
        host.restartEnclave()
        assertThat(client.sendGet(Get("key"))).isEqualTo("value")
    }

    @Test
    fun `multi-threaded enclaves not supported`() {
        threadSafeEnclave = true
        val e = assertThrows<EnclaveLoadException> { client }
        assertThat(e.cause!!).isInstanceOf(IllegalStateException::class.java)
        assertThat(e.cause!!).hasMessageStartingWith("The persistent map is not available in multi-threaded enclaves.")
    }

    @Test
    fun `persistent map not available if not enabled in config`() {
        mockConfiguration.enablePersistentMap = false
        assertThatIllegalStateException().isThrownBy {
            executeActionsLocally(listOf(Put("key", "value")))
        }.withMessageStartingWith("The enclave persistent map is not enabled.")
    }

    @Test
    fun `persistent map cannot store more than configured max size bytes`() {
        mockConfiguration.maxPersistentMapSize = 8192
        assertThatIllegalStateException().isThrownBy {
            executeActionsLocally(listOf(Put("key", "This string repeats too much! ".repeat(512))))
        }.withMessageStartingWith("The persistent map capacity has been exceeded.")
    }

    private fun processActions(restartStrategy: RestartStrategy, writes: List<WriteAction>, get: Get): String? {
        when (restartStrategy) {
            AllWritesInSingleMailWithRestartBeforeRead -> {
                client.sendActionsInSingleMail(writes)
                host.restartEnclave()
            }
            WritePerMailWithRestartAfterEachMail -> {
                for (index in writes.indices) {
                    client.sendSingleAction(writes[index])
                    host.restartEnclave()
                }
            }
            WritePerMailWithRestartBeforeRead -> {
                for (write in writes) {
                    client.sendSingleAction(write)
                }
                host.restartEnclave()
            }
        }

        return client.sendGet(get)
    }

    private fun restartAfterWrite(write: WriteAction, get: Get): String? {
        return processActions(AllWritesInSingleMailWithRestartBeforeRead, listOf(write), get)
    }

    private fun MockClient.sendSingleAction(action: MapAction): EnclaveMail? {
        return sendActionsInSingleMail(listOf(action)).singleOrNull()
    }

    private fun MockClient.sendGet(get: Get): String? {
        val response = sendSingleAction(get)!!
        return get.parse(response.bodyAsBytes)
    }

    private fun MockClient.sendActionsInSingleMail(actions: List<MapAction>): List<EnclaveMail> {
        val mailBody = writeData { serialiseActions(actions) }
        sendMail(mailBody)
        return ArrayList<EnclaveMail>().also(receivedMail::drainTo)
    }

    private fun executeActionsLocally(actions: List<MapAction>, receiverClient: MockClient? = null): List<EnclaveMail> {
        val serialised = writeData {
            nullableWrite(receiverClient) { write(it.publicKey.encoded) }
            if (receiverClient != null) {
                writeUTF(receiverClient.mockHost.getRoutingHint(receiverClient))
            }
            serialiseActions(actions)
        }
        host.enclaveHost.callEnclave(serialised)
        return if (receiverClient != null) {
            ArrayList<EnclaveMail>().also(receiverClient.receivedMail::drainTo)
        } else {
            emptyList()
        }
    }

    private fun DataOutputStream.serialiseActions(actions: List<MapAction>) {
        writeList(actions) { action ->
            val type = when (action) {
                is Put -> 0
                is Remove -> 1
                is Get -> 2
            }
            write(type)
            action.serialise(this)
        }
    }

    private abstract class PersistingEnclave : Enclave() {
        abstract override val threadSafe: Boolean

        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            mail.bodyAsBytes.deserialise {
                processActions(this, postOffice(mail), routingHint)
            }
        }

        override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
            bytes.deserialise {
                val optionalReceiver = nullableRead { Curve25519PublicKey(readExactlyNBytes(32)) }
                val optionalPostOffice = optionalReceiver?.let(::postOffice)
                val routingHint = optionalReceiver?.let { readUTF() }
                processActions(this, optionalPostOffice, routingHint)
            }
            return null
        }

        private fun processActions(dis: DataInputStream, postOffice: EnclavePostOffice?, routingHint: String?) {
            repeat(dis.readInt()) {
                val response = processAction(dis)
                if (response != null && postOffice != null) {
                    postMail(postOffice.encryptMail(response), routingHint)
                }
            }
        }

        private fun processAction(dis: DataInputStream): ByteArray? {
            val action = when (val type = dis.read()) {
                0 -> Put.deserialise(dis)
                1 -> Remove.deserialise(dis)
                2 -> Get.deserialise(dis)
                else -> throw IllegalArgumentException(type.toString())
            }
            return action.apply(this)
        }
    }

    private class ThreadSafePersistingEnclave : PersistingEnclave() {
        override val threadSafe: Boolean get() = true
    }

    private class NonThreadSafePersistingEnclave : PersistingEnclave() {
        override val threadSafe: Boolean get() = false
    }

    private abstract class EchoEnclave : Enclave() {
        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            val answer = mail.bodyAsBytes
            val postOffice = postOffice(mail)
            postMail(postOffice.encryptMail(answer), routingHint)
        }
    }

    private class ThreadSafeEchoEnclave : EchoEnclave() {
        override val threadSafe: Boolean get() = true
    }

    private class NonThreadSafeEchoEnclave : EchoEnclave() {
        override val threadSafe: Boolean get() = false
    }

    private sealed interface MapAction {
        fun serialise(out: DataOutputStream)
        fun apply(enclave: Enclave): ByteArray?

        sealed class WriteAction : MapAction {
            final override fun apply(enclave: Enclave): ByteArray? {
                applyNoReturn(enclave)
                return null
            }

            protected abstract fun applyNoReturn(enclave: Enclave)

            class Put(val key: String, val value: String) : WriteAction() {
                override fun serialise(out: DataOutputStream) {
                    out.writeUTF(key)
                    out.writeUTF(value)
                }
                override fun applyNoReturn(enclave: Enclave) {
                    enclave.persistentMap[key] = value.toByteArray()
                }
                companion object {
                    fun deserialise(dis: DataInputStream): Put = Put(dis.readUTF(), dis.readUTF())
                }
            }

            class Remove(val key: String) : WriteAction() {
                override fun serialise(out: DataOutputStream) = out.writeUTF(key)
                override fun applyNoReturn(enclave: Enclave) {
                    enclave.persistentMap.remove(key)
                }
                companion object {
                    fun deserialise(dis: DataInputStream): Remove = Remove(dis.readUTF())
                }
            }
        }

        class Get(val key: String) : MapAction {
            override fun serialise(out: DataOutputStream) = out.writeUTF(key)
            override fun apply(enclave: Enclave): ByteArray {
                val value = enclave.persistentMap[key]
                return writeData {
                    nullableWrite(value) { write(it) }
                }
            }
            fun parse(bytes: ByteArray): String? {
                return bytes.deserialise {
                    nullableRead { String(readBytes()) }
                }
            }
            companion object {
                fun deserialise(dis: DataInputStream): Get = Get(dis.readUTF())
            }
        }
    }

    enum class RestartStrategy {
        /**
         * Single sealed state generated for all writes, which is fed in an immediate restart.
         */
        AllWritesInSingleMailWithRestartBeforeRead,
        /**
         * Sealed state generated after each write, which is fed (along with the previous cumulative sealed states) in
         * an immediate restart.
         */
        WritePerMailWithRestartAfterEachMail,
        /**
         * Sealed state generated after each write, which are all fed together in a single restart.
         */
        WritePerMailWithRestartBeforeRead
    }


    private class MockHost(
        val enclaveClass: KClass<out Enclave>,
        val mockConfiguration: MockConfiguration
    ) : AutoCloseable {
        var enclaveHost = createMockHost(enclaveClass.java, mockConfiguration)
        private val sealedStates = ArrayList<ByteArray>()
        private val clients = HashBiMap.create<MockClient, String>()

        init {
            enclaveHost.start(null, null, null, ::processCommands)
        }

        fun newClient(client: MockClient) {
            clients[client] = UUID.randomUUID().toString()
        }

        fun deliverMail(mailBytes: ByteArray, client: MockClient) {
            enclaveHost.deliverMail(mailBytes, getRoutingHint(client))
        }

        fun getRoutingHint(client: MockClient): String = clients.getValue(client)

        fun restartEnclave(rollBackNumberOfStates: Int = 0) {
            enclaveHost.close()
            enclaveHost = createMockHost(enclaveClass.java, mockConfiguration)

            val index = sealedStates.lastIndex - rollBackNumberOfStates
            val sealedStateToRestore = if (index != -1) sealedStates[index] else null
            enclaveHost.start(null, sealedStateToRestore, null, ::processCommands)
            clients.keys.forEach { it.enclaveRestarted() }
        }

        private fun processCommands(mailCommands: List<MailCommand>) {
            // Make sure there is one or fewer sealed state commands
            val sealedStateCommands = mailCommands.filterIsInstance<MailCommand.StoreSealedState>()
            when (sealedStateCommands.size) {
                0 -> Unit
                1 -> sealedStates += sealedStateCommands.single().sealedState
                else -> throw IllegalStateException("Too many sealed state commands!")
            }
            for (mailCommand in mailCommands) {
                if (mailCommand is MailCommand.PostMail) {
                    val client = clients.inverse().getValue(mailCommand.routingHint)
                    client.receiveMail(mailCommand.encryptedBytes)
                }
            }
        }

        override fun close() {
            enclaveHost.close()
        }
    }

    private class MockClient(val mockHost: MockHost) {
        private val privatekey = Curve25519PrivateKey.random()
        var postOffice = createPostOffice()
        val receivedMail = LinkedBlockingQueue<EnclaveMail>()
        val publicKey: PublicKey get() = privatekey.publicKey

        init {
            mockHost.newClient(this)
        }

        fun sendMail(body: ByteArray, envelope: ByteArray? = null) {
            mockHost.deliverMail(postOffice.encryptMail(body, envelope), this)
        }

        fun receiveMail(mailBytes: ByteArray) {
            receivedMail += postOffice.decryptMail(mailBytes)
        }

        fun enclaveRestarted() {
            // TODO We wouldn't need to do this if we had a client layer API above PostOffice: https://r3-cev.atlassian.net/browse/CON-617
            val old = postOffice
            postOffice = createPostOffice()
            postOffice.lastSeenStateId = old.lastSeenStateId
        }

        private fun createPostOffice(): PostOffice {
            return mockHost.enclaveHost.enclaveInstanceInfo.createPostOffice(privatekey, "default")
        }
    }
}
