package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import java.nio.ByteBuffer

object EnclaveQuoteServiceMock : EnclaveQuoteService() {

    override fun getQuotingEnclaveInfo(): Cursor<SgxTargetInfo, ByteBuffer> {
        return Cursor.allocate(SgxTargetInfo)
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        //  TODO: refactor the EnclaveQuoteService, and remove this class.
        //  Note that Mock mode will not call t
        //  his code, only Simulation mode will do it
        //    as it is mocking the signed quote.
        val signedQuote = Cursor.wrap(SgxSignedQuote, ByteArray(SgxSignedQuote.minSize))
        // We can populate the other fields as needed, but for now we just need to copy over the report body.
        signedQuote[SgxSignedQuote.quote][SgxQuote.reportBody] = report[SgxReport.body].read()
        return signedQuote
    }
}
