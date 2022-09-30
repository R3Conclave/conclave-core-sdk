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
        println("certificate: " + pckCert.sigAlgName + " " + pckCert.sigAlgOID)
        val bytes = pckCert.sgxExtension.getBytes(SGX_FMSPC_OID).getRemainingBytes()
        println("certificate bytes: ${bytes.contentToString()}")
        println("pckCert.issuerDN.name: ${pckCert.issuerDN.name}")
        val fields = Native.getQuoteCollateral(
            bytes, // fpsmc
            if ("Processor" in pckCert.issuerDN.name) 0 else 1 // pckCert 'type': 0 - Processor, 1 - Platform
        )
        // TODO There's no reason why the JNI can't create the QuoteCollateral directly. It would also fix this hack
        //  with the collateral fields being passed up as Strings when in fact they should be byte arrays.
        val collateral = QuoteCollateral(
            fields[0] as Int,
            fields[1].asStringToBytes(),
            fields[2].asStringToBytes(),
            fields[3].asStringToBytes(),
            fields[4].asStringToBytes(),
            fields[5].asStringToBytes(),
            fields[6].asStringToBytes(),
            fields[7].asStringToBytes()
        )
        return DcapAttestation(signedQuote.asReadOnly(), collateral)
    }

    override fun getFmspc(signedQuote: ByteCursor<SgxSignedQuote>): ByteArray {
        val pckCert = signedQuote.toEcdsaP256AuthData()[qeCertData].toPckCertPath().x509Certs[0]
        println("certificate: " + pckCert.sigAlgName + " " + pckCert.sigAlgOID)
        println("pckCert.issuerDN.name: ${pckCert.issuerDN.name}")
        return pckCert.sgxExtension.getBytes(SGX_FMSPC_OID).getRemainingBytes()
    }

    private fun Any.asStringToBytes(): OpaqueBytes = OpaqueBytes((this as String).toByteArray())
}
