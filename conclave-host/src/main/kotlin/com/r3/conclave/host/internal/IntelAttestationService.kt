package com.r3.conclave.host.internal

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationResponse
import com.r3.sgx.core.host.debug
import com.r3.sgx.core.host.loggerFor
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus.SC_OK
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.RegistryBuilder
import org.apache.http.config.SocketConfig
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.util.EntityUtils
import java.net.URLDecoder
import java.security.SecureRandom
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.*

/**
 * Implementation of Intel's HTTPS attestation service. The API specification is described in
 * https://api.trustedservices.intel.com/documents/sgx-attestation-api-spec.pdf.
 */
class IntelAttestationService(private val url: String, private val subscriptionKey: String) : AttestationService {
    companion object {
        private val log = loggerFor<IntelAttestationService>()

        private val mapper = ObjectMapper()
        private val random = SecureRandom()

        private val httpRequestConfig: RequestConfig = RequestConfig.custom()
                .setConnectTimeout(20_000)
                .setSocketTimeout(5_000)
                .build()
        private val httpSocketConfig: SocketConfig = SocketConfig.custom()
                .setSoReuseAddress(true)
                .setTcpNoDelay(true)
                .build()
    }

    override fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationResponse {
        try {
            createHttpClient().use { client ->
                val httpRequest = HttpPost("$url/attestation/v3/report")
                log.debug { "IAS: $httpRequest" }
                val reportRequest = ReportRequest(isvEnclaveQuote = signedQuote.getBuffer().array())
                httpRequest.entity = StringEntity(mapper.writeValueAsString(reportRequest), ContentType.APPLICATION_JSON)
                httpRequest.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                client.execute(httpRequest).use { httpResponse ->
                    if (httpResponse.statusLine.statusCode != SC_OK) {
                        val content = String(httpResponse.entity.content.readBytes())
                        log.warn("Got IAS reply with status ${httpResponse.statusLine.statusCode}: $content")
                        throw RuntimeException("Error from Intel Attestation Service: $content")
                    }
                    return AttestationResponse(
                            reportBytes = EntityUtils.toByteArray(httpResponse.entity),
                            signature = Base64.getDecoder().decode(httpResponse.requireHeader("X-IASReport-Signature")),
                            certPath = httpResponse.parseResponseCertPath(),
                            advisoryIds = httpResponse.getFirstHeader("Advisory-IDs")?.value?.split(",") ?: emptyList()
                    )
                }
            }
        } catch (error: Exception) {
            log.error("Got IAS exception:", error)
            throw error
        }
    }

    /**
     * The certificate chain is stored in the `X-IASReport-Signing-Certificate` header in PEM format as an URL encoded
     * string.
     */
    private fun CloseableHttpResponse.parseResponseCertPath(): CertPath {
        return requireHeader("X-IASReport-Signing-Certificate").decodeURL().parsePemCertPath()
    }

    private fun HttpResponse.requireHeader(name: String): String {
        return checkNotNull(getFirstHeader(name)?.value) {
            "Response header '$name' missing (${getFirstHeader("Request-ID")})"
        }
    }

    private fun createHttpClient(): CloseableHttpClient {
        val sslContext = SSLContextBuilder()
                .setSecureRandom(random)
                .build()
        val registry = RegistryBuilder.create<ConnectionSocketFactory>()
                .register("https", SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.getDefaultHostnameVerifier()))
                .build()
        return HttpClients.custom()
                .setConnectionManager(BasicHttpClientConnectionManager(registry).apply {
                    socketConfig = httpSocketConfig
                })
                .setDefaultRequestConfig(httpRequestConfig)
                .build()
    }

    private fun String.decodeURL(): String = URLDecoder.decode(this, "UTF-8")

    @JsonPropertyOrder("isvEnclaveQuote", "pseManifest", "nonce")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private class ReportRequest(
            @param:JsonProperty("isvEnclaveQuote")
            val isvEnclaveQuote: ByteArray,

            @param:JsonProperty("pseManifest")
            val pseManifest: ByteArray? = null,

            @param:JsonProperty("nonce")
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
        certificates.add(certificateFactory.generateCertificate(input))
    }
    return certificateFactory.generateCertPath(certificates)
}
