package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.Native
import java.nio.ByteBuffer

class EnclaveQuoteServiceEPID(private val attestationParameters: AttestationParameters.EPID): EnclaveQuoteService() {
    private val quoteResponse = Cursor.allocate(SgxInitQuoteResponse)

    init {
        Native.initQuote(quoteResponse.buffer.array())
    }

    override fun getQuotingEnclaveInfo(): Cursor<SgxTargetInfo, ByteBuffer> {
        return quoteResponse[SgxInitQuoteResponse.quotingEnclaveTargetInfo]
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBytes = ByteArray(Native.calcQuoteSize(null))
        val getQuote = createSgxGetQuote(report)
        Native.getQuote(
            getQuote.buffer.array(),
            null,
            null,
            null,
            quoteBytes
        )
        return Cursor.wrap(SgxSignedQuote, quoteBytes)
    }

    private fun createSgxGetQuote(report: ByteCursor<SgxReport>): Cursor<SgxGetQuote, ByteBuffer> {
        val getQuote = Cursor.allocate(SgxGetQuote)
        getQuote[SgxGetQuote.report] = report.read()
        getQuote[SgxGetQuote.quoteType] = SgxQuoteType.LINKABLE.value
        getQuote[SgxGetQuote.spid] = attestationParameters.spid.buffer()
        return getQuote
    }
}
