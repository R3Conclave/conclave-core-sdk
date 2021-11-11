package com.r3.conclave.host.web

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.PlatformSupportException
import com.r3.conclave.host.internal.EnclaveHostService
import com.r3.conclave.host.internal.loggerFor
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeData
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Path
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@RestController
class EnclaveWebController {
    lateinit var enclaveHostService: EnclaveHostService

    /**
     * MockConfiguration parameters.
     * Only used in Mock mode
     * and are ignored in Simulation/Debug/Release modes.
     */
    @Value("\${mock.code.hash:}")
    var codeHash: String? = null

    @Value("\${mock.code.signing.key.hash:}")
    var codeSigningKeyHash: String? = null

    @Value("\${mock.product.id:}")
    var productID: Int? = null

    @Value("\${mock.revocation.level:}")
    var revocationLevel: Int? = null

    @Value("\${mock.tcb.level:}")
    var tcbLevel: Int? = null

    /**
     * When/if enclave emits StoreSealedState mail command,
     * this is where the sealed state data will be stored.
     *
     * Also, if this file exists, its content will be passed into EnclaveHost.start() method.
     */
    @Value("\${sealed.state.file:}")
    var sealedStateFile: Path? = null

    @Value("\${filesystem.file:}")
    var enclaveFileSystemFile: Path? = null

    @PostConstruct
    fun init() {
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
        enclaveHostService = object : EnclaveHostService() {
            override val enclaveHost: EnclaveHost = EnclaveHost.load(mockConfiguration)
            override val enclaveFileSystemFile: Path? get() = this@EnclaveWebController.enclaveFileSystemFile
            override fun storeSealedState(sealedState: ByteArray) {
                val sealedStateFile = checkNotNull(sealedStateFile) { "sealed.state.file is not set" }
                DataOutputStream(sealedStateFile.outputStream()).use {
                    it.writeIntLengthPrefixBytes(sealedStateHeader)
                    it.write(sealedState)
                }
            }
        }
        val sealedState = loadSealedState()
        enclaveHostService.start(AttestationParameters.DCAP(), sealedState)

        logger.info("Enclave ${enclaveHost.enclaveClassName} started")
        logger.info(enclaveHost.enclaveInstanceInfo.toString())
    }

    private val sealedStateHeader: ByteArray by lazy {
        writeData {
            write(1)  // Version
            write(enclaveHost.enclaveMode.ordinal)
        }
    }

    /**
     * sealed state file might not exist yet, return null then
     */
    private fun loadSealedState(): ByteArray? {
        val sealedStateFile = this.sealedStateFile ?: return null
        if (!sealedStateFile.exists()) return null
        DataInputStream(sealedStateFile.inputStream()).use { stream ->
            val header = stream.readIntLengthPrefixBytes()
            header.deserialise {
                val version = read()
                check(version == 1) { "Version $version of the sealed state file not supported" }
                val savedEnclaveMode = EnclaveMode.values()[stream.read()]
                val runtimeEnclaveMode = enclaveHost.enclaveMode
                check(savedEnclaveMode == runtimeEnclaveMode) {
                    "Unable to restore the enclave's state from $sealedStateFile. This file was encrypted when the " +
                            "enclave was running in $savedEnclaveMode mode but now the enclave is running in " +
                            "$runtimeEnclaveMode mode. The enclave's state cannot migrate across different modes."
                }
            }
            return stream.readBytes()
        }
    }

    @GetMapping("/attestation")
    fun attestation(): ByteArray = enclaveHost.enclaveInstanceInfo.serialize()

    @PostMapping("/deliver-mail")
    fun deliverMail(
        @RequestHeader("Correlation-ID") correlationId: String,
        @RequestBody encryptedMail: ByteArray
    ): ByteArray {
        return enclaveHostService.deliverMail(encryptedMail, correlationId) ?: emptyBytes
    }

    @PostMapping("/poll-mail")
    fun pollMail(@RequestHeader("Correlation-ID") correlationId: String): ByteArray {
        return enclaveHostService.pollMail(correlationId) ?: emptyBytes
    }

    @PreDestroy
    fun shutdown() {
        if (::enclaveHostService.isInitialized) {
            enclaveHostService.close()
        }
    }

    private fun buildMockConfiguration(): MockConfiguration {
        val mockConfiguration = MockConfiguration()
        if (!codeHash.isNullOrEmpty()) {
            mockConfiguration.codeHash = SHA256Hash.parse(codeHash!!)
        }
        if (!codeSigningKeyHash.isNullOrEmpty()) {
            SHA256Hash.parse(codeSigningKeyHash!!)
        }
        productID?.let { mockConfiguration.productID = it }
        revocationLevel?.let { mockConfiguration.revocationLevel = it }
        tcbLevel?.let { mockConfiguration.tcbLevel = it }
        return mockConfiguration
    }

    private val enclaveHost: EnclaveHost get() = enclaveHostService.enclaveHost

    private companion object {
        private val logger = loggerFor<EnclaveWebController>()
        private val emptyBytes = ByteArray(0)
    }
}
