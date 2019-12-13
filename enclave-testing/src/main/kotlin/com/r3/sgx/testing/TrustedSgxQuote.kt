package com.r3.sgx.testing

import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.SgxSignedQuote
import com.r3.sgx.core.common.attestation.AttestedOutput
import com.r3.sgx.core.common.attestation.SgxQuoteReader
import com.r3.sgx.core.common.attestation.Measurement
import java.nio.ByteBuffer

class TrustedSgxQuote(override val data: Cursor<ByteBuffer, SgxQuote>): AttestedOutput<Cursor<ByteBuffer, SgxQuote>> {
    override val source: Measurement
        get() = Measurement.read(SgxQuoteReader(data).measurement)

    companion object {
        fun fromSignedQuote(signedQuoteCursor: Cursor<ByteBuffer, SgxSignedQuote>): TrustedSgxQuote {
            val schema = SgxSignedQuote(signedQuoteCursor.getBuffer().capacity())
            return TrustedSgxQuote(signedQuoteCursor[schema.quote])
        }
    }
}