package com.r3.conclave.host.web

import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.PlatformSupportException
import com.r3.conclave.host.internal.loggerFor
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import java.nio.file.Path
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
    @Value("\${enclave.class:}")
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
    var sealedStateFile: Path? = null

    @PostConstruct
    fun init() {
        require(!enclaveClassName.isNullOrEmpty()) { "Please specify a full class name of your enclave using command line option `--enclave.class`, " +
                "e.g. --enclave.class=package_name.enclave_class_name" }

        if (EnclaveHost.isHardwareEnclaveSupported()) {
            logger.info("This platform supports enclaves in simulation, debug and release mode.")
        } else if (EnclaveHost.isSimulatedEnclaveSupported()) {
            logger.info("This platform does not support hardware enclaves, but does support enclaves in simulation.")
            logger.info("Attempting to enable hardware enclave support...")
            try {
                EnclaveHost.enableHardwareEnclaveSupport()
                logger.info("Hardware support enabled!")
            } catch (e: PlatformSupportException) {
                logger.warn("Failed to enable hardware enclave support. Reason: ${e.message}")
            }
        } else {
            logger.info("This platform supports enclaves in mock mode only.")
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

        logger.info("Enclave ${enclaveHost.enclaveClassName} started")
        logger.info(enclaveHost.enclaveInstanceInfo.toString())
    }

    private fun updateInbox(key: String, encryptedBytes: ByteArray) {
        synchronized(inboxes) {
            val inbox = inboxes.computeIfAbsent(key) { ArrayList() }
            inbox += encryptedBytes
        }
    }

    private fun persistSealedState(stateBlob: ByteArray) {
        val sealedStateFile = checkNotNull(this.sealedStateFile) { "sealed.state.file is not set" }
        sealedStateFile.writeBytes(stateBlob)
    }

    /**
     * sealed state file might not exist yet, return null then
     */
    private fun loadSealedState(): ByteArray? {
        val sealedStateFile = this.sealedStateFile
        return if (sealedStateFile?.exists() == true) sealedStateFile.readBytes() else null
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

    private fun buildMockConfiguration(): MockConfiguration {
        val mockConfiguration = MockConfiguration()
        if (!codeHash.isNullOrEmpty())
            mockConfiguration.codeHash = SHA256Hash.parse(codeHash!!)
        if (!codeSigningKeyHash.isNullOrEmpty())
            mockConfiguration.codeSigningKeyHash = SHA256Hash.parse(codeSigningKeyHash!!)
        mockConfiguration.productID = productID
        mockConfiguration.revocationLevel = revocationLevel
        mockConfiguration.tcbLevel = tcbLevel
        return mockConfiguration
    }

    private companion object {
        private val logger = loggerFor<EnclaveWebController>()
    }
}
