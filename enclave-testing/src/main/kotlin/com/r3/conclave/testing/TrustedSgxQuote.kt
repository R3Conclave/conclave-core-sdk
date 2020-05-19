package com.r3.conclave.testing

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxQuote
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.core.common.attestation.AttestedOutput
import com.r3.conclave.core.common.attestation.Measurement
import com.r3.conclave.core.common.attestation.SgxQuoteReader

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