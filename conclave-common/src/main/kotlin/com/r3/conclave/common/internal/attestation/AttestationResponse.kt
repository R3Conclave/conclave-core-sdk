package com.r3.conclave.common.internal.attestation

import com.fasterxml.jackson.databind.ObjectMapper
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.PKIXParameters

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
        val certPath: CertPath
) {
    companion object {
        private val reportMapper = AttestationReport.register(ObjectMapper())
    }

    fun verify(pkixParameters: PKIXParameters): AttestationReport {
        CertPathValidator.getInstance("PKIX").validate(certPath, pkixParameters)

        Signature.getInstance("SHA256withRSA").apply {
            initVerify(certPath.certificates[0])
            update(reportBytes)
            if (!verify(signature)) {
                throw GeneralSecurityException("Report failed IAS signature check")
            }
        }

        val report = reportMapper.readValue(reportBytes, AttestationReport::class.java)
        // As advised in the SGX docs
        check(report.version == 4) {
            "The attestation service is running a different version (${report.version}) to the one expected (4). Conclave needs to be updated."
        }
        return report
    }
}
