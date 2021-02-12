package com.r3.conclave.common.internal.attestation

import java.security.cert.CertPath
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL

data class QuoteCollateral(
    val version: String,
    val pckCrlIssuerChain: String,
    val rawRootCaCrl: String,
    val rawPckCrl: String,
    val rawTcbInfoIssuerChain: String,
    val rawSignedTcbInfo: String,
    val rawQeIdentityIssuerChain: String,
    val rawSignedQeIdentity: String
) {
    val rootCaCrl: X509CRL by lazy { parseCRL(rawRootCaCrl) }

    val pckCrl: X509CRL by lazy { parseCRL(rawPckCrl) }

    val tcbInfoIssuerChain: CertPath by lazy { parseCertPath(rawTcbInfoIssuerChain) }

    val signedTcbInfo: SignedTcbInfo by lazy {
        attestationObjectMapper.readValue(rawSignedTcbInfo, SignedTcbInfo::class.java)
    }

    val qeIdentityIssuerChain: CertPath by lazy { parseCertPath(rawQeIdentityIssuerChain) }

    val signedQeIdentity: SignedEnclaveIdentity by lazy {
        attestationObjectMapper.readValue(rawSignedQeIdentity, SignedEnclaveIdentity::class.java)
    }

    private fun parseCertPath(pem: String): CertPath {
        // TODO Don't convert the cert paths in the JNI code to Strings. Keep them as the raw byte arrays.
        return AttestationUtils.parsePemCertPath(pem)
    }

    private fun parseCRL(pem: String): X509CRL {
        // TODO We're converting the CRL back into bytes here, so the JNI code should also keep the CRL fields as raw bytes.
        return CertificateFactory.getInstance("X.509").generateCRL(pem.byteInputStream()) as X509CRL
    }
}
