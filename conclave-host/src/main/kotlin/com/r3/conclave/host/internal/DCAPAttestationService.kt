package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeCertData
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationUtils.SGX_FMSPC_OID
import com.r3.conclave.common.internal.attestation.AttestationUtils.sgxExtension
import com.r3.conclave.common.internal.attestation.DcapAttestation
import com.r3.conclave.common.internal.attestation.QuoteCollateral
import com.r3.conclave.common.internal.toEcdsaP256AuthData
import com.r3.conclave.common.internal.toPckCertPath
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.x509Certs

class DCAPAttestationService(override val isRelease: Boolean) : HardwareAttestationService() {
    override fun doAttestQuote(signedQuote: ByteCursor<SgxSignedQuote>): DcapAttestation {
        val pckCert = signedQuote.toEcdsaP256AuthData()[qeCertData].toPckCertPath().x509Certs[0]
        val col = Native.getQuoteCollateral(
            fmspc = pckCert.sgxExtension.getBytes(SGX_FMSPC_OID).getRemainingBytes(),
            pck = if ("Processor" in pckCert.issuerDN.name) 0 else 1
        )
        // TODO There's no reason why the JNI can't create the QuoteCollateral directly. Doing so allows the properties
        //      to have better types, such as int for the version. The other fields can also just be byte arrays as
        //      converting them to Strings may be unnecessary as they need to be parsed.
        val collateral = QuoteCollateral(col[0], col[1], col[2], col[3], col[4], col[5], col[6], col[7])
        return DcapAttestation(signedQuote.asReadOnly(), collateral)
    }
}
