package com.r3.conclave.internaltesting

import com.r3.conclave.client.EnclaveClient
import com.r3.conclave.client.EnclaveTransport
import com.r3.conclave.common.*
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.internal.EnclaveHostService
import com.r3.conclave.host.internal.createMockHost
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.reflect.KClass

class MockEnclaveTransport(
    private val enclaveClass: KClass<out Enclave>,
    private val configuration: MockConfiguration? = null
) : EnclaveTransport, AutoCloseable {
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val sealedStates = ArrayList<ByteArray>()
    private var enclaveHostService = MockEnclaveHostService(enclaveClass, configuration)

    fun startEnclave() {
        enclaveHostService.start(null, null, null, null)
    }

    val enclaveHost: EnclaveHost get() = enclaveHostService.enclaveHost

    @Synchronized
    fun restartEnclave(rollBackNumberOfStates: Int = 0, configuration: MockConfiguration? = this.configuration) {
        enclaveHostService.close()
        enclaveHostService = MockEnclaveHostService(enclaveClass, configuration)

        val index = sealedStates.lastIndex - rollBackNumberOfStates
        val sealedStateToRestore = if (index != -1) sealedStates[index] else null
        enclaveHostService.start(null, sealedStateToRestore, null, null)
    }

    fun startNewClient(): EnclaveClient {
        val client = EnclaveClient(EnclaveConstraint().apply {
            acceptableCodeHashes += enclaveHost.enclaveInstanceInfo.enclaveInfo.codeHash
            minSecurityLevel = EnclaveSecurityInfo.Summary.INSECURE
        })
        client.start(this)
        return client
    }

    override fun enclaveInstanceInfo(): EnclaveInstanceInfo {
        return executeTaskOnHost { enclaveHost.enclaveInstanceInfo }
    }

    override fun connect(client: EnclaveClient): ClientConnection = ClientConnection(client)

    override fun close() {
        executor.shutdown()
        enclaveHostService.close()
        executor.shutdownNow()
    }

    private fun <T> executeTaskOnHost(task: EnclaveHostService.() -> T): T {
        val future = executor.submit(Callable {
            val enclaveHostService = synchronized(this) { enclaveHostService }
            task(enclaveHostService)
        })

        try {
            return future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    inner class ClientConnection(client: EnclaveClient) : EnclaveTransport.ClientConnection {
        val id = SHA256Hash.hash(client.clientPrivateKey.encoded).toString()

        override fun sendMail(encryptedMailBytes: ByteArray): ByteArray? {
            return executeTaskOnHost { deliverMail(encryptedMailBytes, id) }
        }

        override fun pollMail(): ByteArray? {
            return executeTaskOnHost { pollMail(id) }
        }

        override fun disconnect() = Unit
    }

    private inner class MockEnclaveHostService(
        enclaveClass: KClass<out Enclave>,
        mockConfiguration: MockConfiguration?
    ) : EnclaveHostService() {
        override val enclaveHost: EnclaveHost = createMockHost(enclaveClass.java, mockConfiguration)
        override fun storeSealedState(sealedState: ByteArray) {
            sealedStates += sealedState
        }
    }
}
