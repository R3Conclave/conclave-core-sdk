package com.r3.conclave.client.web

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.client.EnclaveClient
import com.r3.conclave.client.EnclaveTransport
import com.r3.conclave.client.EnclaveTransport.ClientConnection
import com.r3.conclave.common.EnclaveException
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.mail.MailDecryptionException
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.entity.EntityBuilder
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.message.BasicHeader
import java.io.Closeable
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.net.ssl.SSLContext

/**
 * An [EnclaveTransport] that uses HTTP to communciate with the host of an enclave. Specifically, this transport
 * implementation is intended to be used when the enclave host is using `conclave-web-host`.
 *
 * Pass an instance of this class to [EnclaveClient.start] to connect an [EnclaveClient] to the host. A single
 * [WebEnclaveTransport] can support multiple clients. When closing this transport, make sure all connected clients have
 * first disconnected by calling [EnclaveClient.close].
 *
 * An [SSLContext] object can be used if a custom SSL setup is required, for example to connect to a web server using a
 * self-signed certificate.
 *
 * @property timeout The connection timeout, defaults to 3 minutes.
 *
 * @see EnclaveClient
 */
class WebEnclaveTransport(
    baseUrl: String,
    val timeout: Duration,
    sslContext: SSLContext?
) : EnclaveTransport, Closeable {
    constructor(baseUrl: String) : this(baseUrl, Duration.ofMinutes(3), null)

    private val baseUri: URI
    private val httpClient: CloseableHttpClient

    init {
        baseUri = URI(baseUrl)
        val connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
        if (sslContext != null) {
            connectionManagerBuilder.setSSLSocketFactory(SSLConnectionSocketFactory(sslContext))
        }
        val requestConfig = RequestConfig.custom().setConnectTimeout(timeout.toMillis(), MILLISECONDS).build()
        httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManagerBuilder.build())
            .setDefaultRequestConfig(requestConfig)
            .build()
    }

    @Throws(IOException::class)
    override fun enclaveInstanceInfo(): EnclaveInstanceInfo {
        val bytes = doRequest(HttpGet(baseUri.resolve("/attestation")))
        return EnclaveInstanceInfo.deserialize(bytes)
    }

    @Throws(IOException::class)
    override fun connect(client: EnclaveClient): ClientConnection = ClientConnectionImpl(client)

    @Throws(IOException::class)
    override fun close() {
        httpClient.close()
    }

    private fun doRequest(request: ClassicHttpRequest): ByteArray {
        return httpClient.execute(request).use { response ->
            if (response.code != HttpStatus.SC_OK) {
                throw IOException(EntityUtils.toString(response.entity))
            }
            EntityUtils.toByteArray(response.entity)
        }
    }

    private inner class ClientConnectionImpl(client: EnclaveClient) : ClientConnection {
        private val correlationIdHeader = BasicHeader(
            "Correlation-ID",
            // Create a correlation ID that this is deterministic (so we don't have to worry about persisting it),
            // unique to the client and which can't be guessed.
            SHA256Hash.hash(client.clientPrivateKey.encoded),
            true  // Sensitive flag
        )

        override fun sendMail(encryptedMailBytes: ByteArray): ByteArray? {
            val httpPost = HttpPost(baseUri.resolve("/deliver-mail")).apply {
                addHeader(correlationIdHeader)
                entity = EntityBuilder.create().setBinary(encryptedMailBytes).build()
            }

            httpClient.execute(httpPost).use { response ->
                val responseBytes = EntityUtils.toByteArray(response.entity)
                if (response.code == HttpStatus.SC_OK) {
                    // Empty bytes represents no mail response.
                    return responseBytes.takeUnless { it.isEmpty() }
                }
                if (response.code == HttpStatus.SC_BAD_REQUEST) {
                    val errorResponse = try {
                        objectMapper.readTree(responseBytes)
                    } catch (e: JsonParseException) {
                        throw IOException("Received invalid error response (${String(responseBytes)})", e)
                    }
                    val message = errorResponse["message"]?.textValue()
                    val error = errorResponse["error"]?.textValue()
                    throw when (error) {
                        "MAIL_DECRYPTION" -> MailDecryptionException(message)
                        "ENCLAVE_EXCEPTION" -> EnclaveException(message)
                        else -> IOException("Received unknown error ($error)")
                    }
                }
                throw IOException("HTTP ${response.code}: ${String(responseBytes)}")
            }
        }

        override fun pollMail(): ByteArray? {
            val httpPost = HttpPost(baseUri.resolve("/poll-mail"))
            httpPost.addHeader(correlationIdHeader)
            // Empty bytes represents no mail.
            return doRequest(httpPost).takeUnless { it.isEmpty() }
        }

        override fun disconnect() {
            // No-op
        }
    }

    private companion object {
        private val objectMapper = ObjectMapper()
    }
}
