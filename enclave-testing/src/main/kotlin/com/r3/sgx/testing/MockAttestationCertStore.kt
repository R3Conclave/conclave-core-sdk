package com.r3.sgx.testing

import java.lang.IllegalStateException
import java.security.cert.*

object MockAttestationCertStore {

    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")

    /**
     * Creates a preconfigured PKIXParameters using test root certificate loaded by enclavelet-host in simulation mode.
     */
    @JvmStatic
    fun loadTestPkix(): PKIXParameters {
        val trustAnchor = TrustAnchor(loadCertificate() as X509Certificate, null)
        val pkixParameters = PKIXParameters(setOf(trustAnchor))
        pkixParameters.isRevocationEnabled = false
        return pkixParameters
    }

    private fun loadCertificate(): Certificate {
        val stream = javaClass.getResourceAsStream("/mock-as/mock-as-root-cert.pem")
                ?: throw IllegalStateException("Cannot load certificate file")
        return certificateFactory.generateCertificate(stream)
    }
}