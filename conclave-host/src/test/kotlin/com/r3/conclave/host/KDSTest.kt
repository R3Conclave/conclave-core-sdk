package com.r3.conclave.host

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.common.internal.kds.KDSErrorResponse
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.host.kds.KDSConfiguration
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.Closeable
import java.io.IOException
import java.net.*
import java.time.Duration
import java.util.*

class KDSTest {
    companion object {
        private const val PORT = 8091

        // Override enclave properties to enable the KDS
        private val ENCLAVE_PROPERTIES_OVERRIDE = Properties().apply {
            setProperty("kds.configurationPresent", "true")
            setProperty("kds.kdsEnclaveConstraint", "S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE")
            setProperty("kds.persistenceKeySpec.configurationPresent", "true")
            setProperty("kds.persistenceKeySpec.masterKeyType", "debug")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.useOwnCodeHash", "true")
            setProperty("kds.persistenceKeySpec.policyConstraint.constraint", "SEC:INSECURE")
        }

        private fun mockHost(): EnclaveHost {
            return createMockHost(KDSConfiguredEnclave::class.java, null, ENCLAVE_PROPERTIES_OVERRIDE)
        }

        private fun httpServer(httpHandler: HttpHandler): Closeable {
            val httpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 1)
            httpServer.createContext("/private", httpHandler)
            return CloseableHttpServer(httpServer)
        }

        private fun startHost(enclaveHost: EnclaveHost, kdsConfiguration: KDSConfiguration? = KDSConfiguration("http://localhost:$PORT")) {
            enclaveHost.start(null, null, null, kdsConfiguration) {}
        }
    }

    private class CloseableHttpServer(val httpServer: HttpServer) : Closeable {
        init {
            httpServer.start()
        }

        override fun close() {
            httpServer.stop(0)
        }
    }

    @Test
    fun `malformed URL`() {
        val brokenProtocol = "broken"
        val url = "$brokenProtocol://url:$PORT"
        mockHost().use { host ->
            assertThatExceptionOfType(Exception::class.java).isThrownBy {
                startHost(host, KDSConfiguration(url))
            }.withCauseExactlyInstanceOf(MalformedURLException::class.java)
                    .withStackTraceContaining("unknown protocol: $brokenProtocol")
        }
    }

    @Test
    fun `unknown host`() {
        val unknownURL = "unknown"
        val url = "http://$unknownURL:$PORT"
        mockHost().use { host ->
            assertThatExceptionOfType(Exception::class.java).isThrownBy {
                startHost(host, KDSConfiguration(url))
            }.withCauseExactlyInstanceOf(UnknownHostException::class.java)
                    .withStackTraceContaining(unknownURL)
        }
    }

    @Test
    fun `connection refused`() {
        mockHost().use { host ->
            assertThatExceptionOfType(Exception::class.java).isThrownBy {
                startHost(host)
            }.withCauseExactlyInstanceOf(ConnectException::class.java)
                    .withStackTraceContaining("Connection refused")
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [
        HttpURLConnection.HTTP_BAD_REQUEST,
        HttpURLConnection.HTTP_NOT_FOUND,
        HttpURLConnection.HTTP_INTERNAL_ERROR])
    fun `error codes and exceptions`(responseStatus: Int) {
        val kdsException = KDSErrorResponse("any exception message")
        httpServer {
            val response = ObjectMapper().writeValueAsBytes(kdsException)
            it.sendResponseHeaders(responseStatus, response.size.toLong())
            it.responseBody.write(response)
        }.use {
            mockHost().use { host ->
                assertThatExceptionOfType(Exception::class.java).isThrownBy {
                    startHost(host)
                }.withCauseExactlyInstanceOf(IOException::class.java)
                        .withStackTraceContaining(kdsException.reason)
            }
        }
    }

    @Test
    fun `unknown key type`() {
        val kdsException = KDSErrorResponse("The requested master key type is not supported")
        httpServer {
            val response = ObjectMapper().writeValueAsBytes(kdsException)
            it.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, response.size.toLong())
            it.responseBody.write(response)
        }.use {
            mockHost().use { host ->
                assertThatExceptionOfType(Exception::class.java).isThrownBy {
                    startHost(host)
                }.withCauseExactlyInstanceOf(IOException::class.java)
                        .withStackTraceContaining(kdsException.reason)
            }
        }
    }

    @Test
    fun timeout() {
        httpServer {
            // Do not respond
        }.use {
            val url = "http://localhost:$PORT"
            var startTime = Long.MAX_VALUE
            val kdsConfiguration = KDSConfiguration(url)
            kdsConfiguration.timeout = Duration.ofSeconds(2)
            mockHost().use { host ->
                assertThatExceptionOfType(Exception::class.java).isThrownBy {
                    startTime = System.currentTimeMillis()
                    startHost(host, kdsConfiguration)
                }.withCauseExactlyInstanceOf(SocketTimeoutException::class.java)
                        .withStackTraceContaining("timed out")
            }
            val endTime = System.currentTimeMillis()
            assertThat(endTime - startTime).isGreaterThanOrEqualTo(kdsConfiguration.timeout.toMillis())
        }
    }

    class KDSConfiguredEnclave : Enclave()
}
