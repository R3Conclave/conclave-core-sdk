package com.r3.sgx.enclavelethost.server.internal

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.conclave.host.internal.AttestationService
import com.r3.sgx.core.common.ChannelInitiatingHandler
import com.r3.sgx.core.common.ErrorHandler
import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.core.common.ThrowingErrorHandler
import com.r3.sgx.core.host.*
import java.io.File

/**
 * Represents the possible states of an hosted enclavelet and provides the logic to
 * transition between states
 */
sealed class EnclaveletState {

    /**
     * Enclavelet loaded from file
     */
    open class Created(val enclaveHandle: EnclaveHandle<*>,
                       val channels: ChannelInitiatingHandler.Connection,
                       val mux: SimpleMuxingHandler.Connection): EnclaveletState() {

        fun requestAttestation(
                attestationService: AttestationService,
                attestationConfig: EpidAttestationHostConfiguration
        ) : Attested {
            log.info("Getting quote from enclave")
            val attestation = mux.addDownstream(EpidAttestationHostHandler(attestationConfig))
            val rawQuote = attestation.getSignedQuote()

            log.info("Getting IAS signature")
            val response = attestationService.requestSignature(rawQuote)
            return Attested(this, rawQuote, response)
        }

        companion object {
            fun fromErrorHandler(enclaveHandle: EnclaveHandle<ErrorHandler.Connection>): Created {
                val error = enclaveHandle.connection
                val mux = error.setDownstream(SimpleMuxingHandler())
                val channels = mux.addDownstream(ChannelInitiatingHandler())
                return Created(enclaveHandle, channels, mux)
            }
        }

    }

    /**
     * Enclavelet loaded with attestation report attached
     */
    class Attested(
            created: Created,
            val rawQuote: ByteCursor<SgxSignedQuote>,
            val attestationResponse: AttestationResponse
    ) : Created(created.enclaveHandle, created.channels, created.mux)

    companion object {
        private val log = loggerFor<EnclaveletState>()

        fun load(enclaveFile: File, loadMode: EnclaveLoadMode): Created {
            val hostApi = NativeHostApi(loadMode)
            log.info("Using enclave load mode: $loadMode")
            val handle = hostApi.createEnclave(
                    ThrowingErrorHandler(),
                    enclaveFile
            )
            return Created.fromErrorHandler(handle)
        }
    }
}

