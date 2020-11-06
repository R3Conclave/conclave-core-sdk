package com.r3.conclave.host.internal

import com.r3.conclave.common.AttestationMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeCertData
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.SgxSignedQuote.signature
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.conclave.common.internal.attestation.DCAPUtils
import com.r3.conclave.common.internal.attestation.QuoteCollateral
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.security.cert.CertificateFactory

object DCAPAttestationService : AttestationService {
    override fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationResponse {
        val signature = signedQuote[signature].read().getRemainingBytes()
        val authData = Cursor.wrap(SgxEcdsa256BitQuoteAuthData, signature)
        val pckCertPath = authData[qeCertData].toPckCertPath()

        val col = Native.getQuoteCollateral(
                DCAPUtils.getFMSPC(pckCertPath),
                DCAPUtils.getPckWord(pckCertPath)
        )
        val collateral = QuoteCollateral(col[0], col[1], col[2], col[3], col[4], col[5], col[6], col[7])

        // TODO AttestationResponse is the wrong class to carry the DCAP attestation data.
        return AttestationResponse(
                signedQuote[quote].bytes,
                signature,
                pckCertPath,
                collateral,
                AttestationMode.DCAP
        )
    }
}
