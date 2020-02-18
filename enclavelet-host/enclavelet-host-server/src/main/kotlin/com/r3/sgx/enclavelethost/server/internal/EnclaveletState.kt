package com.r3.sgx.enclavelethost.server.internal

import com.r3.conclave.host.internal.AttestationResponse
import com.r3.conclave.host.internal.AttestationService
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

        fun requestAttestation(attestationService: AttestationService,
                               attestationConfig: EpidAttestationHostConfiguration)
                : Attested {

            log.info("Getting quote from enclave")
            val attestation = mux.addDownstream(EpidAttestationHostHandler(attestationConfig))
            val rawQuote = attestation.getQuote()

            log.info("Getting IAS signature")
            val iasResponse = attestationService.requestSignature(rawQuote)
            return Attested(this, rawQuote, iasResponse)
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
        val log: Logger = LoggerFactory.getLogger(EnclaveletState::class.java)

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

