package com.r3.conclave.common.internal.attestation

import com.r3.conclave.common.AttestationMode
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxQuote
import com.r3.conclave.common.internal.SgxSignedQuote
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.PKIXParameters
import java.time.Instant

/**
 * A cryptographically signed report produced by an [AttestationService] verifying an enclave's quote ([SgxSignedQuote]).
 *
 * @property reportBytes Serialised [AttestationReport]
 *
 * @property signature Signature produced by the attestation service over [reportBytes].
 *
 * @property certPath Certificate chain for the public key that created the signature. The validity of this chain needs
 * to be checked against the well-known root certificate of the attestation service.
 */
class AttestationResponse(
        val reportBytes: ByteArray,
        val signature: ByteArray,
        val certPath: CertPath,
        val collateral: QuoteCollateral, // only used for DCAP
        val attestationMode: AttestationMode
) {
    fun verify(pkixParameters: PKIXParameters): AttestationReport {
        return if (attestationMode == AttestationMode.DCAP) verifyECDSA() else verifyRSA(pkixParameters)
    }

    private fun verifyRSA(pkixParameters: PKIXParameters): AttestationReport {
        CertPathValidator.getInstance("PKIX").validate(certPath, pkixParameters)

        Signature.getInstance("SHA256withRSA").apply {
            initVerify(certPath.certificates[0])
            update(reportBytes)
            if (!verify(signature)) {
                throw GeneralSecurityException("Attestation report failed signature check")
            }
        }

        val report = attestationObjectMapper.readValue(reportBytes, AttestationReport::class.java)
        // As advised in the SGX docs
        check(report.version == 4) {
            "The attestation service is running a different version (${report.version}) to the one expected (4). Conclave needs to be updated."
        }
        return report
    }

    private fun verifyECDSA(): AttestationReport {
        QuoteVerifier.verify(reportBytes, signature, certPath, collateral)

        val ts = Instant.now()
        return AttestationReport(
            ts.toEpochMilli().toString(),
            QuoteStatus.OK,
            Cursor.wrap(SgxQuote, reportBytes),
            timestamp = ts,
            version = 3
        )
    }
}


class QuoteCollateral (
        val version: String,
        val pckCrlIssuerChain: String,
        val rootCaCrl: String,
        val pckCrl: String,
        val tcbInfoIssuerChain: String,
        val tcbInfo: String,
        val qeIdentityIssuerChain: String,
        val qeIdentity: String
) {
    companion object {
        fun mock(): QuoteCollateral {
            return QuoteCollateral(
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    ""
            )
        }
    }
}
