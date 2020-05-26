package com.r3.conclave.host.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.conclave.core.host.debug
import com.r3.conclave.core.host.loggerFor
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_OK
import java.net.URL
import java.net.URLDecoder
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.*

/**
 * Implementation of Intel's HTTPS attestation service. The API specification is described in
 * https://api.trustedservices.intel.com/documents/sgx-attestation-api-spec.pdf.
 */
class IntelAttestationService(private val isProd: Boolean, private val subscriptionKey: String) : AttestationService {
    companion object {
        private val logger = loggerFor<IntelAttestationService>()
        private val objectMapper = ObjectMapper()
    }

    override fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationResponse {
        val baseUrl = if (isProd) {
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
                objectMapper.writeValue(it, ReportRequest(isvEnclaveQuote = signedQuote.getBuffer().array()))
            }
            if (connection.responseCode != HTTP_OK) {
                throw IOException("Error response from Intel Attestation Service (${connection.responseCode}): " +
                        connection.errorStream?.readFully()?.let { String(it) })
            }
            return AttestationResponse(
                    reportBytes = connection.inputStream.readFully(),
                    signature = Base64.getDecoder().decode(connection.getHeaderField("X-IASReport-Signature")),
                    certPath = connection.parseResponseCertPath()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readFully(): ByteArray = use { it.readBytes() }

    /**
     * The certificate chain is stored in the `X-IASReport-Signing-Certificate` header in PEM format as an URL encoded
     * string.
     */
    private fun HttpURLConnection.parseResponseCertPath(): CertPath {
        val urlEncodedPemCertPath = getHeaderField("X-IASReport-Signing-Certificate")
        val pemCertPathString = URLDecoder.decode(urlEncodedPemCertPath, "UTF-8")
        return pemCertPathString.parsePemCertPath()
    }

    private class ReportRequest(
            val isvEnclaveQuote: ByteArray,
            val pseManifest: ByteArray? = null,
            val nonce: String? = null
    )
}

/**
 * Parses a PEM encoded string into a [CertPath]. The certificates in the chain are assumed to be appended to each other.
 */
internal fun String.parsePemCertPath(): CertPath {
    val certificateFactory = CertificateFactory.getInstance("X.509")
    val certificates = mutableListOf<Certificate>()
    val input = byteInputStream()
    while (input.available() > 0) {
        certificates += certificateFactory.generateCertificate(input)
    }
    return certificateFactory.generateCertPath(certificates)
}
