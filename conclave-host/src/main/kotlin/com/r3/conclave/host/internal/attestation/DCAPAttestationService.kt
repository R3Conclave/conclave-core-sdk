package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeCertData
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationUtils.SGX_FMSPC_OID
import com.r3.conclave.common.internal.attestation.AttestationUtils.sgxExtension
import com.r3.conclave.common.internal.attestation.DcapAttestation
import com.r3.conclave.common.internal.attestation.QuoteCollateral
import com.r3.conclave.common.internal.toEcdsaP256AuthData
import com.r3.conclave.common.internal.toPckCertPath
import com.r3.conclave.host.internal.Native
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.x509Certs

class DCAPAttestationService(override val isRelease: Boolean) : HardwareAttestationService() {
    override fun doAttestQuote(signedQuote: ByteCursor<SgxSignedQuote>): DcapAttestation {
        val pckCert = signedQuote.toEcdsaP256AuthData()[qeCertData].toPckCertPath().x509Certs[0]
        val col = Native.getQuoteCollateral(
            pckCert.sgxExtension.getBytes(SGX_FMSPC_OID).getRemainingBytes(), // fpsmc
            if ("Processor" in pckCert.issuerDN.name) 0 else 1 // pckCert 'type': 0 - Processor, 1 - Platform
        )
        // TODO There's no reason why the JNI can't create the QuoteCollateral directly. Doing so allows the properties
        //      to have better types, such as int for the version. The other fields can also just be byte arrays as
        //      converting them to Strings may be unnecessary as they need to be parsed.
        val collateral = QuoteCollateral(
            col[0] as Int,
            col[1] as String,
            col[2] as String,
            col[3] as String,
            col[4] as String,
            col[5] as String,
            col[6] as String,
            col[7] as String
        )
        return DcapAttestation(signedQuote.asReadOnly(), collateral)
    }
}
