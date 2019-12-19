package com.r3.sgx.enclavelethost.server.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxSignedQuote
import com.r3.sgx.enclavelethost.server.AttestationService
import com.r3.sgx.enclavelethost.server.AttestationServiceReportResponse
import com.r3.sgx.enclavelethost.server.internal.ias.AttestationError
import com.r3.sgx.enclavelethost.server.internal.ias.ReportRequest
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus.SC_FORBIDDEN
import org.apache.http.HttpStatus.SC_OK
import org.apache.http.client.config.RequestConfig
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
import org.jboss.resteasy.util.Base64
import org.slf4j.LoggerFactory
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.ws.rs.ForbiddenException
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriBuilder

class IntelAttestationService(private val url: String, private val subscriptionKey: String) : AttestationService {

    companion object {
        @JvmStatic
        private val log = LoggerFactory.getLogger(IntelAttestationService::class.java)

        private val mapper = ObjectMapper().registerModule(JavaTimeModule())
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

    override fun requestSignature(signedQuote: Cursor<ByteBuffer, SgxSignedQuote>): AttestationServiceReportResponse {
        val rawQuote = signedQuote.getBuffer().array()
        try {
            createHttpClient().use { client ->
                val reportURI = UriBuilder.fromUri(url)
                        .path("attestation/v3/report")
                        .build()
                log.info("Invoking IAS: {}", reportURI)

                val httpRequest = HttpPost(reportURI)
                val reportRequest = ReportRequest(rawQuote)
                httpRequest.entity = StringEntity(mapper.writeValueAsString(reportRequest), ContentType.APPLICATION_JSON)
                httpRequest.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                client.execute(httpRequest).use { httpResponse ->
                    if (httpResponse.statusLine.statusCode != SC_OK) {
                        val content = String(httpResponse.entity.content.readBytes())
                        log.warn("Got IAS reply with status ${httpResponse.statusLine.statusCode}: $content")
                        throw RuntimeException("Error from Intel Attestation Service: $content")
                    }
                    return IasProxyResponse(
                            signature = Base64.decode(httpResponse.requireHeader("X-IASReport-Signature")),
                            certificate = httpResponse.requireHeader("X-IASReport-Signing-Certificate").decodeURL(),
                            httpResponse = EntityUtils.toByteArray(httpResponse.entity))
                }
            }
        } catch (error: Exception) {
            log.error("Got IAS exception:", error)
            throw error
        }
    }

    private fun HttpResponse.toResponse(message: String, statusCode: Int = statusLine.statusCode): Response {
        return Response.status(statusCode)
                .entity(AttestationError(message))
                .apply {
                    val requestIdHeader = getFirstHeader("Request-ID") ?: return@apply
                    this.header(requestIdHeader.name, requestIdHeader.value)
                }
                .build()
    }

    private fun HttpResponse.requireHeader(name: String): String {
        return getFirstHeader(name)?.value ?: throw ForbiddenException(toResponse("Response header '$name' missing", SC_FORBIDDEN))
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
}

class IasProxyResponse(
        override val httpResponse: ByteArray,
        override val signature: ByteArray,
        override val certificate: String
) : AttestationServiceReportResponse
