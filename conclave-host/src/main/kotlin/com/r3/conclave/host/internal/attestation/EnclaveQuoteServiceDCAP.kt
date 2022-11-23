package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.internal.Native
import com.r3.conclave.host.internal.NativeLoader
import java.nio.ByteBuffer

object EnclaveQuoteServiceDCAP : EnclaveQuoteService() {
    
    // In the context of signing the quote, targetInfo has always the same value.
    //    It identifies the quoting enclave as the enclave that will verify
    //    the report generated when signing the quote.
    private val targetInfo = Cursor.allocate(SgxTargetInfo).also {
        Native.initQuoteDCAP(
            NativeLoader.libsPath.toString(),
            true,
            it.buffer.array()
        )
    }

    override fun getQuotingEnclaveInfo(): Cursor<SgxTargetInfo, ByteBuffer> {
        return targetInfo
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBytes = ByteArray(Native.calcQuoteSizeDCAP())
        val getQuote = createSgxGetQuote(report)
        Native.getQuoteDCAP(getQuote.buffer.array(), quoteBytes)
        return Cursor.wrap(SgxSignedQuote, quoteBytes)
    }

    private fun createSgxGetQuote(report: ByteCursor<SgxReport>): Cursor<SgxGetQuote, ByteBuffer> {
        val getQuote = Cursor.allocate(SgxGetQuote)
        getQuote[SgxGetQuote.report] = report.read()
        return getQuote
    }
}
