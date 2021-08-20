package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.Native
import java.nio.ByteBuffer

class EnclaveQuoteServiceEPID(val attestationParameters: AttestationParameters.EPID): EnclaveQuoteService() {

    override fun initializeQuote(): Cursor<SgxTargetInfo, ByteBuffer> {
        val quoteResponse = Cursor.allocate(SgxInitQuoteResponse)
        Native.initQuote(quoteResponse.buffer.array())
        return quoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo]
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBytes = ByteArray(Native.calcQuoteSize(null))
        val getQuote = createSgxGetQuote(report)
        Native.getQuote(
            getQuote.buffer.array(),
            signatureRevocationListIn = null,
            // TODO Do we need to use the nonce?
            quotingEnclaveReportNonceIn = null,
            quotingEnclaveReportOut = null,
            quoteOut = quoteBytes
        )
        val signedQuote = Cursor.wrap(SgxSignedQuote, quoteBytes)
        return signedQuote
    }

    private fun createSgxGetQuote(report: ByteCursor<SgxReport>): Cursor<SgxGetQuote, ByteBuffer> {
        val getQuote = Cursor.allocate(SgxGetQuote)
        getQuote[SgxGetQuote.report] = report.read()
        getQuote[SgxGetQuote.quoteType] = SgxQuoteType.LINKABLE.value
        getQuote[SgxGetQuote.spid] = attestationParameters.spid.buffer()
        return getQuote
    }
}
