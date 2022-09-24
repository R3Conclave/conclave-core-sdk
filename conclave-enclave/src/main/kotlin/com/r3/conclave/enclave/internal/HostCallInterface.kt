package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.attestation.Attestation

abstract class HostCallInterface : CallInitiator<HostCallType>, CallAcceptor<EnclaveCallType>() {
    companion object {
        private const val MISSING_RETURN_VALUE_ERROR_MESSAGE = "Missing host call return buffer"
    }

    /**
     * Get a signed quote from the host.
     */
    fun getSignedQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBuffer = checkNotNull(initiateCall(HostCallType.GET_SIGNED_QUOTE, report.buffer)) {
            MISSING_RETURN_VALUE_ERROR_MESSAGE
        }
        return Cursor.slice(SgxSignedQuote, quoteBuffer)
    }

    /**
     * Get quoting enclave info from the host.
     */
    fun getQuotingEnclaveInfo(): ByteCursor<SgxTargetInfo> {
        val infoBuffer = checkNotNull(initiateCall(HostCallType.GET_QUOTING_ENCLAVE_INFO)) {
            MISSING_RETURN_VALUE_ERROR_MESSAGE
        }
        return Cursor.slice(SgxTargetInfo, infoBuffer)
    }

    /**
     * Request an attestation from the host.
     */
    fun getAttestation(): Attestation {
        val buffer = checkNotNull(initiateCall(HostCallType.GET_ATTESTATION)) {
            MISSING_RETURN_VALUE_ERROR_MESSAGE
        }
        return Attestation.getFromBuffer(buffer)
    }
}
