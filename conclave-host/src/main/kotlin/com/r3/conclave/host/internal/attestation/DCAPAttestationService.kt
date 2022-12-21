package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.OpaqueBytes
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
        val fields = Native.getQuoteCollateral(
            pckCert.sgxExtension.getBytes(SGX_FMSPC_OID).getRemainingBytes(), // fpsmc
            if ("Processor" in pckCert.issuerDN.name) 0 else 1 // pckCert 'type': 0 - Processor, 1 - Platform
        )
        val collateral = QuoteCollateral(
            fields[0] as Int,
            fields[1].toOpaqueBytes(),
            fields[2].toOpaqueBytes(),
            fields[3].toOpaqueBytes(),
            fields[4].toOpaqueBytes(),
            fields[5].toOpaqueBytes(),
            fields[6].toOpaqueBytes(),
            fields[7].toOpaqueBytes()
        )
        return DcapAttestation(signedQuote.asReadOnly(), collateral)
    }

    private fun Any.toOpaqueBytes(): OpaqueBytes = OpaqueBytes(this as ByteArray)
}
