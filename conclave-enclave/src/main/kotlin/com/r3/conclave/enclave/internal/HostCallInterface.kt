package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*

abstract class HostCallInterface : CallInitiator<HostCallType>, CallAcceptor<EnclaveCallType>() {
    /**
     * Get a signed quote from the host.
     */
    fun getSignedQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBuffer = initiateCall(HostCallType.GET_SIGNED_QUOTE, report.buffer)
        return Cursor.slice(SgxSignedQuote, quoteBuffer)
    }

    /**
     * Get quoting enclave info from the host.
     */
    fun getQuotingEnclaveInfo(): ByteCursor<SgxTargetInfo> {
        val infoBuffer = initiateCall(HostCallType.GET_QUOTING_ENCLAVE_INFO)
        return Cursor.slice(SgxTargetInfo, infoBuffer)
    }
}
