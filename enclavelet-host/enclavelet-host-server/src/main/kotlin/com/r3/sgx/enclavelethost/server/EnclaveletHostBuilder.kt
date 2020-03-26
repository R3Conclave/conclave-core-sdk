package com.r3.sgx.enclavelethost.server

import com.google.common.io.BaseEncoding
import com.r3.conclave.host.internal.IntelAttestationService
import com.r3.conclave.host.internal.MockAttestationService
import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxSpid
import com.r3.sgx.core.host.EnclaveLoadMode
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import com.r3.sgx.core.host.loggerFor
import com.r3.sgx.enclavelethost.server.internal.EnclaveletState
import java.io.File
import java.nio.ByteBuffer

class EnclaveletHostBuilder(
        private val configuration: EnclaveletHostConfiguration,
        private val enclaveletFile: File
) {
    companion object {
        private val log = loggerFor<EnclaveletHostBuilder>()
    }

    private val attestationConfiguration = EpidAttestationHostConfiguration(
            quoteType = configuration.epidQuoteType,
            spid = Cursor(SgxSpid, ByteBuffer.wrap(BaseEncoding.base16().decode(configuration.epidSpid)))
    )

    fun build(): EnclaveletHost {
        val attestationService = when (configuration.enclaveLoadMode) {
            EnclaveLoadMode.SIMULATION -> MockAttestationService()
            else -> IntelAttestationService(configuration.attestationServiceUrl, configuration.iasSubscriptionKey)
        }
        log.info("Loading enclave from: ${enclaveletFile.path}")
        val loadedEnclavelet = EnclaveletState.load(enclaveletFile, configuration.enclaveLoadMode)
        val serverEnclavelet = when (configuration.enclaveLoadMode) {
            EnclaveLoadMode.SIMULATION -> {
                if (configuration.mockAttestationServiceInSimulation) {
                    log.info("Using mock remote attestation service in simulation mode")
                    loadedEnclavelet.requestAttestation(attestationService, attestationConfiguration)
                } else {
                    log.warn("Intel remote attestation report not available in simulation mode")
                    loadedEnclavelet
                }
            }
            else -> {
                log.info("Requesting attestation report")
                loadedEnclavelet.requestAttestation(attestationService, attestationConfiguration)
            }
        }
        return EnclaveletHost(serverEnclavelet, configuration)
    }
}
