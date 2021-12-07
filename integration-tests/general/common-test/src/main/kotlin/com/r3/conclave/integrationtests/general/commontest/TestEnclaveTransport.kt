package com.r3.conclave.integrationtests.general.commontest

import com.r3.conclave.client.EnclaveClient
import com.r3.conclave.client.EnclaveTransport
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveSecurityInfo
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.internal.EnclaveHostService
import com.r3.conclave.host.kds.KDSConfiguration
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

// This is largely a copy of com.r3.conclave.internaltesting.MockEnclaveTransport
open class TestEnclaveTransport(
    private val enclaveClassName: String,
    private val enclaveFileSystemFile: Path?,
    private val kdsUrl: String?
) : EnclaveTransport, AutoCloseable {
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private var sealedState: ByteArray? = null

    private var _enclaveHostService = TestEnclaveHostService(enclaveClassName)
    val enclaveHostService: EnclaveHostService get() = _enclaveHostService

    open val attestationParameters: AttestationParameters? get() = null

    fun startEnclave() {
        _enclaveHostService.start(
            attestationParameters,
            sealedState,
            enclaveFileSystemFile,
            kdsUrl?.let(::KDSConfiguration)
        )
    }

    val enclaveHost: EnclaveHost get() = enclaveHostService.enclaveHost

    @Synchronized
    fun restartEnclave() {
        enclaveHostService.close()
        _enclaveHostService = TestEnclaveHostService(enclaveClassName)
        startEnclave()
    }

    fun startNewClient(): EnclaveClient {
        val client = EnclaveClient(EnclaveConstraint().apply {
            acceptableCodeHashes.add(enclaveHost.enclaveInstanceInfo.enclaveInfo.codeHash)
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
            return executeTaskOnHost { deliverMail(encryptedMailBytes, id, null) }
        }

        override fun pollMail(): ByteArray? {
            return executeTaskOnHost { pollMail(id) }
        }

        override fun disconnect() = Unit
    }

    private inner class TestEnclaveHostService(enclaveClassName: String) : EnclaveHostService() {
        private val enclaveHost: EnclaveHost = EnclaveHost.load(enclaveClassName)
        override fun getEnclaveHost(): EnclaveHost = enclaveHost
        override fun storeSealedState(sealedState: ByteArray) {
            this@TestEnclaveTransport.sealedState = sealedState
        }
    }
}
