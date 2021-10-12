package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.Native
import com.r3.conclave.host.internal.NativeLoader
import java.nio.ByteBuffer

class EnclaveQuoteServiceDCAP(val attestationParameters: AttestationParameters.DCAP): EnclaveQuoteService() {

    override fun initializeQuote(): Cursor<SgxTargetInfo, ByteBuffer> {
        val targetInfo = Cursor.allocate(SgxTargetInfo)
        Native.initQuoteDCAP(NativeLoader.libsPath.toString(), targetInfo.buffer.array())
        return targetInfo
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBytes = ByteArray(Native.calcQuoteSizeDCAP())
        val getQuote = createSgxGetQuote(report)
        Native.getQuoteDCAP(getQuote.buffer.array(), quoteBytes)
        val signedQuote = Cursor.wrap(SgxSignedQuote, quoteBytes)
        return signedQuote
    }

    private fun createSgxGetQuote(report: ByteCursor<SgxReport>): Cursor<SgxGetQuote, ByteBuffer> {
        val getQuote = Cursor.allocate(SgxGetQuote)
        getQuote[SgxGetQuote.report] = report.read()
        return getQuote
    }
}