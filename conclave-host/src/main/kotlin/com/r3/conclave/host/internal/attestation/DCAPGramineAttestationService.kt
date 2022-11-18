package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxEcdsa256BitQuoteAuthData.qeCertData
import com.r3.conclave.common.internal.attestation.AttestationUtils.SGX_FMSPC_OID
import com.r3.conclave.common.internal.attestation.AttestationUtils.sgxExtension
import com.r3.conclave.common.internal.attestation.DcapAttestation
import com.r3.conclave.common.internal.attestation.QuoteCollateral
import com.r3.conclave.host.internal.Native
import com.r3.conclave.host.internal.NativeLoader
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.x509Certs

class DCAPGramineAttestationService(override val isRelease: Boolean) : HardwareAttestationService() {
    override fun doAttestQuote(signedQuote: ByteCursor<SgxSignedQuote>): DcapAttestation {
        val fmspc = getFmspc(signedQuote)
        val fields = Native.getQuoteCollateral(fmspc, 1)

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

    private fun getFmspc(signedQuote: ByteCursor<SgxSignedQuote>): ByteArray {
        val pckCert = signedQuote.toEcdsaP256AuthData()[qeCertData].toPckCertPath().x509Certs[0]
        return pckCert.sgxExtension.getBytes(SGX_FMSPC_OID).getRemainingBytes()
    }

    private fun Any.asStringToBytes(): OpaqueBytes = OpaqueBytes((this as String).toByteArray())
}
