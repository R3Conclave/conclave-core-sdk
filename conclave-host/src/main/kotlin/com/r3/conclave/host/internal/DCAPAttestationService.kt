package com.r3.conclave.host.internal

import com.r3.conclave.common.AttestationMode
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.SgxSignedQuote.signature
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.conclave.common.internal.attestation.QuoteCollateral

object DCAPAttestationService : AttestationService {
    private const val VersionIndex = 0
    private const val PckCrlIssuerChainIndex = 1
    private const val RootCaCrlIndex = 2
    private const val PckCrlIndex = 3
    private const val TcbInfoIssuerChainIndex = 4
    private const val TcbInfoIndex = 5
    private const val QeIdentityIssuerChainIndex = 6
    private const val QeIdentityIndex = 7

    override fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationResponse {
        val report = signedQuote[quote].bytes
        val signature = signedQuote[signature].bytes
        val certPath = DCAPUtils.parsePemCertPathFromSignature(signature)
        val col = Native.getQuoteCollateral(
                DCAPUtils.getFMSPC(certPath),
                DCAPUtils.getPckWord(certPath)
        )
        val collateral = QuoteCollateral(
                col[VersionIndex],
                col[PckCrlIssuerChainIndex],
                col[RootCaCrlIndex],
                col[PckCrlIndex],
                col[TcbInfoIssuerChainIndex],
                col[TcbInfoIndex],
                col[QeIdentityIssuerChainIndex],
                col[QeIdentityIndex],
        )
        return AttestationResponse(report, signature, certPath, collateral, AttestationMode.DCAP)
    }
}
