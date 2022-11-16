package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.*
import java.nio.ByteBuffer

// Note: we actually do not need this class in Gramine
// TODO: Avoid calling initializeQuote in Gramine and remove this class
class EnclaveGramineQuoteServiceDCAP : EnclaveQuoteService() {

    override fun initializeQuote(): Cursor<SgxTargetInfo, ByteBuffer> {
        // This is called in GRAMINE context but not really useful
        // TODO: Improve this mechanism
        return Cursor.allocate(SgxTargetInfo)
    }

    override fun retrieveQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        // This is actually not called in GRAMINE
        // TODO: Improve this mechanism
        return Cursor.wrap(SgxSignedQuote, byteArrayOf())
    }
}
