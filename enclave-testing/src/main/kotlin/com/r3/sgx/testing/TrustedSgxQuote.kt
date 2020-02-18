package com.r3.sgx.testing

import com.r3.sgx.core.common.ByteCursor
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.SgxSignedQuote
import com.r3.sgx.core.common.attestation.AttestedOutput
import com.r3.sgx.core.common.attestation.Measurement
import com.r3.sgx.core.common.attestation.SgxQuoteReader

class TrustedSgxQuote(override val data: ByteCursor<SgxQuote>): AttestedOutput<ByteCursor<SgxQuote>> {
    override val source: Measurement
        get() = Measurement.read(SgxQuoteReader(data).measurement)

    companion object {
        fun fromSignedQuote(signedQuoteCursor: ByteCursor<SgxSignedQuote>): TrustedSgxQuote {
            val schema = SgxSignedQuote(signedQuoteCursor.getBuffer().capacity())
            return TrustedSgxQuote(signedQuoteCursor[schema.quote])
        }
    }
}