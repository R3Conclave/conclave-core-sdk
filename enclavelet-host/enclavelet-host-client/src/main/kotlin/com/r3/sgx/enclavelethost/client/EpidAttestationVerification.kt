package com.r3.sgx.enclavelethost.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.r3.conclave.host.internal.QuoteStatus
import com.r3.conclave.host.internal.ReportResponse
import com.r3.sgx.core.common.*
import com.r3.sgx.core.common.attestation.AttestedOutput
import com.r3.sgx.core.common.attestation.Measurement
import com.r3.sgx.core.common.attestation.SgxQuoteReader
import com.r3.sgx.enclavelethost.grpc.EpidAttestation
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.Signature
import java.security.cert.*

/**
 * This is a utility class providing basic checks of an EPID-based attestation. You can use this class directly or use
 * [EpidAttestationVerificationBuilder].
 *
 * @property acceptGroupOutOfDate specifies whether a GROUP_OUT_OF_DATE status is acceptable.
 * @property acceptConfigurationNeeded specifies whether a CONFIGURATION_NEEDED status is acceptable.
 * @property acceptDebug specifies whether a quote originating from an enclave loaded in DEBUG mode is acceptable.
 *     WARNING: Only use [acceptDebug] for testing non-release enclaves! It is *not* safe to accept DEBUG quotes in
 *     production!
 * @property quoteConstraints specifies a list of quote verification rules (see [QuoteConstraint])
 */
class EpidAttestationVerification(
        val acceptGroupOutOfDate: Boolean,
        val acceptConfigurationNeeded: Boolean,
        val acceptDebug: Boolean,
        val quoteConstraints: List<QuoteConstraint>
) {
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")

    /**
     * Verifies the passed in attestation evidence, and returns the quote data embedded in the IAS response.
     *
     * @param pkixParameters the PKIX parameters to check the IAS signature against. See [loadIntelPkix] to create a
     *     preconfigured one.
     * @param attestation the attestation to check, retrieved from an enclavelet host.
     * @return the quote body extracted from the signed IAS response.
     */
    fun verify(pkixParameters: PKIXParameters, attestation: EpidAttestation): AttestedOutput<ByteCursor<SgxQuote>> {
        val certificatePath = parseCertificates(attestation.iasCertificate)

        val certValidator = CertPathValidator.getInstance("PKIX")
        certValidator.validate(certificatePath, pkixParameters)

        val signature = Signature.getInstance("SHA256withRSA").apply {
            initVerify(certificatePath.certificates[0])
        }

        signature.update(attestation.iasResponse.asReadOnlyByteBuffer())
        if (!signature.verify(attestation.iasSignature.toByteArray())) {
            throw GeneralSecurityException("Report failed IAS signature check")
        }

        val response = reportResponseDeserialiseMapper.readValue<ReportResponse>(attestation.iasResponse.newInput())
        check(response.version == 3)  // As advised in the SGX docs

        val status = response.isvEnclaveQuoteStatus
        when {
            status == QuoteStatus.OK -> {}
            status == QuoteStatus.GROUP_OUT_OF_DATE && acceptGroupOutOfDate -> {}
            status == QuoteStatus.CONFIGURATION_NEEDED && acceptConfigurationNeeded -> {}
            else -> {
                throw GeneralSecurityException("Report status untrusted (${status.name})")
            }
        }

        val quoteCursor = Cursor(SgxQuote, ByteBuffer.wrap(response.isvEnclaveQuoteBody))
        val quoteReader = SgxQuoteReader(quoteCursor)
        quoteConstraints.forEach { constraint -> constraint.verify(quoteCursor) }
        val flagsCursor = quoteReader.attributesCursor[SgxAttributes.flags]
        val flags = flagsCursor.read()
        if (flags and SgxEnclaveFlags.INITTED == 0L) {
            throw GeneralSecurityException("Expected ${SgxEnclaveFlags::INITTED.name} to be set in quote attributes ($flagsCursor)")
        }
        if (flags and SgxEnclaveFlags.MODE64BIT == 0L) {
            throw GeneralSecurityException("Expected ${SgxEnclaveFlags::MODE64BIT.name} to be set in quote attributes ($flagsCursor)")
        }
        if (flags and SgxEnclaveFlags.DEBUG != 0L && !acceptDebug) {
            throw GeneralSecurityException("Expected ${SgxEnclaveFlags::DEBUG.name} to be unset in quote attributes ($flagsCursor)")
        }
        return TrustedSgxQuote(quoteCursor, Measurement.read(quoteReader.measurement))
    }

    private fun parseCertificates(iasCertificateHeader: String): CertPath {
        val certificates = mutableListOf<Certificate>()
        iasCertificateHeader.byteInputStream().use { input ->
            while (input.available() > 0) {
                certificates.add(certificateFactory.generateCertificate(input))
            }
        }
        return certificateFactory.generateCertPath(certificates)
    }

    private fun loadIntelCaCertificate(): Certificate {
        return certificateFactory.generateCertificate(javaClass.getResourceAsStream("/AttestationReportSigningCACert.pem"))
    }

    /**
     * Creates a preconfigured PKIXParameters with Intel's root certificate as the trust anchor.
     */
    fun loadIntelPkix(): PKIXParameters {
        val certificate = loadIntelCaCertificate()
        val trustAnchor = TrustAnchor(certificate as X509Certificate, null)
        val pkixParameters = PKIXParameters(setOf(trustAnchor))
        val certPathChecker = CertPathValidator.getInstance("PKIX").revocationChecker as PKIXRevocationChecker
        pkixParameters.addCertPathChecker(certPathChecker)
        return pkixParameters
    }

    companion object {
        private val reportResponseDeserialiseMapper = ReportResponseDeserializer.register(ObjectMapper())
    }

    private class TrustedSgxQuote(
            override val data: ByteCursor<SgxQuote>,
            override val source: Measurement
    ): AttestedOutput<ByteCursor<SgxQuote>>
}