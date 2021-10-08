package com.r3.conclave.host

import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.host.EnclaveHost.Companion.checkPlatformSupportsEnclaves
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import java.nio.file.Paths
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

@RestController
class EnclaveWebController {
    private lateinit var enclaveHost: EnclaveHost
    private val inboxes = HashMap<String, MutableList<ByteArray>>()

    /**
     * Enclave class to be loaded
     */
    @Value("\${enclave.class}")
    var enclaveClassName: String? = null

    /**
     * MockConfiguration parameters.
     * Only used in Mock mode
     * and are ignored in Simulation/Debug/Release modes.
     */
    @Value("\${mock.code.hash:}")
    var codeHash: String? = null

    @Value("\${mock.code.signing.key.hash:}")
    var codeSigningKeyHash: String? = null

    @Value("\${mock.product.id:1}")
    var productID: Int = 1

    @Value("\${mock.revocation.level:0}")
    var revocationLevel: Int = 0

    @Value("\${mock.tcb.level:1}")
    var tcbLevel: Int = 1

    /**
     * When/if enclave emits StoreSealedState mail command,
     * this is where the sealed state data will be stored.
     *
     * Also, if this file exists, it's content will be passed into EnclaveHost.start() method.
     */
    @Value("\${sealed.state.file:}")
    var sealedStateFile: String? = null

    @PostConstruct
    fun init() {
        require(!enclaveClassName.isNullOrEmpty()) { "enclave.class is not set" }

        try {
            checkPlatformSupportsEnclaves(true)
            println("This platform supports enclaves in simulation, debug and release mode.")
        } catch (e: MockOnlySupportedException) {
            println("This platform only supports mock enclaves: " + e.message)
        } catch (e: EnclaveLoadException) {
            println("This platform does not support hardware enclaves: " + e.message)
        }

        val mockConfiguration = buildMockConfiguration()
        val sealedState = loadSealedState()

        enclaveHost = EnclaveHost.load(enclaveClassName!!, mockConfiguration)
        enclaveHost.start(AttestationParameters.DCAP(), sealedState) { commands: List<MailCommand> ->
            for (command in commands) {
                when (command) {
                    is MailCommand.PostMail -> updateInbox(command.routingHint!!, command.encryptedBytes)
                    is MailCommand.StoreSealedState -> persistSealedState(command.sealedState)
                }
            }
        }
    }

    private fun updateInbox(key: String, encryptedBytes: ByteArray) {
        synchronized(inboxes) {
            val inbox = inboxes.computeIfAbsent(key) { ArrayList() }
            inbox += encryptedBytes
        }
    }

    private fun persistSealedState(stateBlob: ByteArray) {
        require(!sealedStateFile.isNullOrEmpty()) { "sealed.state.file is not set" }
        Paths.get(sealedStateFile).writeBytes(stateBlob)
    }

    /**
     * sealed state file might not exist yet, return null then
     */
    private fun loadSealedState(): ByteArray? {
        if (sealedStateFile.isNullOrEmpty() || !Paths.get(sealedStateFile).exists())
            return null
        return Paths.get(sealedStateFile).readBytes()
    }

    @GetMapping("/attestation")
    fun attestation(): ByteArray = enclaveHost.enclaveInstanceInfo.serialize()

    @PostMapping("/deliver-mail")
    fun deliverMail(@RequestHeader("Correlation-ID") correlationId: String, @RequestBody encryptedMail: ByteArray) {
        enclaveHost.deliverMail(encryptedMail, correlationId)
    }

    @PostMapping("/inbox")
    fun inbox(@RequestHeader("Correlation-ID") correlationId: String): List<ByteArray> {
        return synchronized(inboxes) { inboxes.remove(correlationId) } ?: emptyList()
    }

    @PreDestroy
    fun shutdown() {
        if (::enclaveHost.isInitialized) {
            enclaveHost.close()
        }
    }

    private fun buildMockConfiguration(): MockConfiguration? {
        var mockConfiguration = MockConfiguration()
        if (!codeHash.isNullOrEmpty())
            mockConfiguration.codeHash = SHA256Hash.parse(codeHash!!)
        if (!codeSigningKeyHash.isNullOrEmpty())
            mockConfiguration.codeSigningKeyHash = SHA256Hash.parse(codeSigningKeyHash!!)
        mockConfiguration.productID = productID
        mockConfiguration.revocationLevel = revocationLevel
        mockConfiguration.tcbLevel = tcbLevel
        return mockConfiguration
    }
}

