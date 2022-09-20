package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*

abstract class HostCallInterface : CallInitiator<HostCallType>, CallAcceptor<EnclaveCallType>() {
    fun getSignedQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBuffer = initiateCall(HostCallType.GET_SIGNED_QUOTE, report.buffer)
        return Cursor.slice(SgxSignedQuote, quoteBuffer!!)
    }
}
