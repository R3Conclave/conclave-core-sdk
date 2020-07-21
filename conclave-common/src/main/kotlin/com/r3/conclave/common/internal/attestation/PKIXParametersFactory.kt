package com.r3.conclave.common.internal.attestation

import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.*

interface PKIXParametersFactory {
    // By declaring these factories as singleton classes we can take advantage of the JVM's lazy classloading and not
    // have to worry about loading certs which we may not use.
    // TODO Do we do soft-fail revocation checking?
    object Intel : AbstractPKIXParametersFactory("Intel")
    object Mock : AbstractPKIXParametersFactory("Mock")

    /**
     * Create a [PKIXParameters] which uses the given [time] for checking the validity of the certificates. If [time] is
     * null then the current time is used.
     */
    fun create(time: Instant?): PKIXParameters

    abstract class AbstractPKIXParametersFactory(prefix: String) : PKIXParametersFactory {
        // The trustAnchors set is fully immutable so it's OK to share it across PKIXParameters instances.
        private val trustAnchors: Set<TrustAnchor>
        init {
            val rootCert = javaClass.getResourceAsStream("/${prefix}AttestationReportSigningCACert.pem").use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            trustAnchors = setOf(TrustAnchor(rootCert, null))
        }

        override fun create(time: Instant?): PKIXParameters {
            return PKIXParameters(trustAnchors).apply {
                isRevocationEnabled = false
                date = time?.let(Date::from)
            }
        }
    }
}
