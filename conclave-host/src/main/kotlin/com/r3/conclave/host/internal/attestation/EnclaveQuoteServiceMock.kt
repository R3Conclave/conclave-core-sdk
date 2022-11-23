package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import java.lang.IllegalStateException
import java.nio.ByteBuffer

object EnclaveQuoteServiceMock : EnclaveQuoteService() {

    override fun getQuotingEnclaveInfo(): Cursor<SgxTargetInfo, ByteBuffer> {
        return Cursor.allocate(SgxTargetInfo)
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        //  TODO: refactor the EnclaveQuoteService, and remove this class.
        throw IllegalStateException("Mock mode should not call retrieveQuote")
    }
}
