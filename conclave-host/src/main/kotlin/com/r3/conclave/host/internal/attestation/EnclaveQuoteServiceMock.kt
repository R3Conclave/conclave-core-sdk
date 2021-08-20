package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import java.nio.ByteBuffer

object EnclaveQuoteServiceMock : EnclaveQuoteService() {

    override fun initializeQuote(): Cursor<SgxTargetInfo, ByteBuffer> {
        val targetInfo = Cursor.allocate(SgxTargetInfo)
        return targetInfo
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val signedQuote = Cursor.wrap(SgxSignedQuote, ByteArray(SgxSignedQuote.minSize))
        // We can populate the other fields as needed, but for now we just need to copy over the report body.
        signedQuote[SgxSignedQuote.quote][SgxQuote.reportBody] = report[SgxReport.body].read()
        return signedQuote
    }
}
