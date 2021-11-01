package com.r3.conclave.host

import com.r3.conclave.client.EnclaveClient
import com.r3.conclave.client.EnclaveRollbackException
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.enclave.EnclavePostOffice
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.Get
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.WriteAction
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.WriteAction.Put
import com.r3.conclave.host.EnclavePersistentMapMockTest.MapAction.WriteAction.Remove
import com.r3.conclave.host.EnclavePersistentMapMockTest.RestartStrategy.*
import com.r3.conclave.internaltesting.MockEnclaveTransport
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMail
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
import java.util.*

class EnclavePersistentMapMockTest {
    private var useEchoEnclave = false
    private var threadSafeEnclave = false
    private var _enclaveTransport: MockEnclaveTransport? = null
    private var _client: EnclaveClient? = null

    private val mockConfiguration = MockConfiguration().apply {
        enablePersistentMap = true
    }

    @AfterEach
    fun cleanUp() {
        _client?.close()
        _enclaveTransport?.close()
    }

    private val enclaveTransport: MockEnclaveTransport get() {
        return _enclaveTransport ?: run {
            val enclaveClass = if (useEchoEnclave) {
                if (threadSafeEnclave) ThreadSafeEchoEnclave::class
                else NonThreadSafeEchoEnclave::class
            } else {
                if (threadSafeEnclave) ThreadSafePersistentMapEnclave::class
                else NonThreadSafePersistentMapEnclave::class
            }
            MockEnclaveTransport(enclaveClass, mockConfiguration).also {
                it.startEnclave()
                _enclaveTransport = it
            }
        }
    }

    private val client: EnclaveClient get() {
        return _client ?: run {
            enclaveTransport.startNewClient().also { _client = it }
        }
    }

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
        assertThat(client.lastSeenStateId).isNull()

        // Restart the enclave and send another mail item
        enclaveTransport.restartEnclave()
        Random().nextBytes(outbound)

        client.sendMail(outbound)
        assertThat(client.lastSeenStateId).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3])
    fun `host unable to roll back state if client has received explicit response`(rollBackState: Int) {
        client.sendSingleAction(Put("key", "v1"))
        client.sendSingleAction(Put("key", "v2"))
        client.sendSingleAction(Get("key"))
        enclaveTransport.restartEnclave(rollBackNumberOfStates = rollBackState)
        assertThatThrownBy {
            client.sendSingleAction(Get("key"))
        }.isInstanceOf(EnclaveRollbackException::class.java)
    }

    // TODO Requires mail acks: https://r3-cev.atlassian.net/browse/CON-616
    @Disabled
    @Test
    fun `host unable to roll back state even if client hasn't received explicit response`() {
        client.sendSingleAction(Put("key", "v1"))
        client.sendSingleAction(Put("key", "v2"))
        enclaveTransport.restartEnclave(rollBackNumberOfStates = 1)
        assertThatThrownBy {
            client.sendSingleAction(Get("key"))
        }.isInstanceOf(EnclaveRollbackException::class.java)
    }

    @Test
    fun `changes to the persistent map made by receiveFromUntrustedHost are preserved`() {
        executeActionsLocally(listOf(Put("key", "value")))
        enclaveTransport.restartEnclave()
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
                enclaveTransport.restartEnclave()
            }
            WritePerMailWithRestartAfterEachMail -> {
                for (index in writes.indices) {
                    client.sendSingleAction(writes[index])
                    enclaveTransport.restartEnclave()
                }
            }
            WritePerMailWithRestartBeforeRead -> {
                for (write in writes) {
                    client.sendSingleAction(write)
                }
                enclaveTransport.restartEnclave()
            }
        }

        return client.sendGet(get)
    }

    private fun restartAfterWrite(write: WriteAction, get: Get): String? {
        return processActions(AllWritesInSingleMailWithRestartBeforeRead, listOf(write), get)
    }

    private fun EnclaveClient.sendSingleAction(action: MapAction): EnclaveMail? {
        return sendActionsInSingleMail(listOf(action))
    }

    private fun EnclaveClient.sendGet(get: Get): String? {
        val response = sendSingleAction(get)!!
        return get.parse(response.bodyAsBytes)
    }

    private fun EnclaveClient.sendActionsInSingleMail(actions: List<MapAction>): EnclaveMail? {
        val mailBody = writeData { serialiseActions(actions) }
        return sendMail(mailBody)
    }

    private fun executeActionsLocally(actions: List<MapAction>, receiverClient: EnclaveClient? = null): EnclaveMail? {
        val serialised = writeData {
            nullableWrite(receiverClient) { write(it.clientPublicKey.encoded) }
            if (receiverClient != null) {
                val mockConnection = receiverClient.clientConnection as MockEnclaveTransport.ClientConnection
                writeUTF(mockConnection.id)
            }
            serialiseActions(actions)
        }
        enclaveTransport.enclaveHost.callEnclave(serialised)
        return receiverClient?.pollMail()
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

    private abstract class PersistentMapEnclave : Enclave() {
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

    private class ThreadSafePersistentMapEnclave : PersistentMapEnclave() {
        override val threadSafe: Boolean get() = true
    }

    private class NonThreadSafePersistentMapEnclave : PersistentMapEnclave() {
        override val threadSafe: Boolean get() = false
    }

    private abstract class EchoEnclave : Enclave() {
        abstract override val threadSafe: Boolean

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
}
