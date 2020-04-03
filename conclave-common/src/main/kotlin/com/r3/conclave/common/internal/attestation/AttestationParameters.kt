package com.r3.conclave.common.internal.attestation

import java.security.cert.*

object AttestationParameters {
    val INTEL: PKIXParameters by lazy {
        val trustAnchor = TrustAnchor(loadCertificate("Intel"), null)
        // TODO Do we do soft-fail revocation checking?
        val pkixParameters = PKIXParameters(setOf(trustAnchor))
        pkixParameters.isRevocationEnabled = false
        pkixParameters
    }

    val MOCK: PKIXParameters by lazy {
        val trustAnchor = TrustAnchor(loadCertificate("Mock"), null)
        val pkixParameters = PKIXParameters(setOf(trustAnchor))
        pkixParameters.isRevocationEnabled = false
        pkixParameters
    }

    private fun loadCertificate(prefix: String): X509Certificate {
        return javaClass.getResourceAsStream("/${prefix}AttestationReportSigningCACert.pem").use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }
}
