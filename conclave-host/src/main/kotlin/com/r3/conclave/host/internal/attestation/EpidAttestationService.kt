package com.r3.conclave.host.internal.attestation

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.SgxSignedQuote.quote
import com.r3.conclave.common.internal.attestation.AttestationUtils
import com.r3.conclave.common.internal.attestation.EpidAttestation
import com.r3.conclave.common.internal.attestation.attestationObjectMapper
import com.r3.conclave.host.internal.debug
import com.r3.conclave.host.internal.loggerFor
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.readFully
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_OK
import java.net.URL
import java.net.URLDecoder
import java.security.cert.CertPath
import java.util.*

/**
 * Implementation of Intel's HTTPS attestation service. The API specification is described in
 * https://api.trustedservices.intel.com/documents/sgx-attestation-api-spec.pdf.
 */
class EpidAttestationService(override val isRelease: Boolean, private val subscriptionKey: String) :
    HardwareAttestationService() {
    companion object {
        private val logger = loggerFor<EpidAttestationService>()
    }

    override fun doAttestQuote(signedQuote: ByteCursor<SgxSignedQuote>): EpidAttestation {
        val baseUrl = if (isRelease) {
            "https://api.trustedservices.intel.com/sgx"
        } else {
            "https://api.trustedservices.intel.com/sgx/dev"
        }
        val url = URL("$baseUrl/attestation/v4/report")
        logger.debug { "Connecting to $url" }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey)
            connection.outputStream.use {
                attestationObjectMapper.writeValue(
                    it,
                    ReportRequest(isvEnclaveQuote = signedQuote.buffer.getRemainingBytes(avoidCopying = true))
                )
            }
            if (connection.responseCode != HTTP_OK) {
                throw IOException("Error response from Intel Attestation Service (${connection.responseCode}): " +
                        connection.errorStream?.readFully()?.let { String(it) })
            }
            val attestation = EpidAttestation(
                reportBytes = OpaqueBytes(connection.inputStream.readFully()),
                signature = OpaqueBytes(Base64.getDecoder().decode(connection.getHeaderField("X-IASReport-Signature"))),
                certPath = connection.parseResponseCertPath(),
            )
            check(attestation.report.isvEnclaveQuoteBody == signedQuote[quote]) {
                "The quote in the EPID attestation report is not the one that was provided to the attestation service."
            }
            return attestation
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The certificate chain is stored in the `X-IASReport-Signing-Certificate` header in PEM format as an URL encoded
     * string.
     */
    private fun HttpURLConnection.parseResponseCertPath(): CertPath {
        val urlEncodedPemCertPath = getHeaderField("X-IASReport-Signing-Certificate")
        val pemCertPathString = URLDecoder.decode(urlEncodedPemCertPath, "UTF-8")
        return AttestationUtils.parsePemCertPath(pemCertPathString.byteInputStream())
    }

    private class ReportRequest(
        val isvEnclaveQuote: ByteArray,
        val pseManifest: ByteArray? = null,
        val nonce: String? = null
    )
}
