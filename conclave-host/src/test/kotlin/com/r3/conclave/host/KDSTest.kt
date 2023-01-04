package com.r3.conclave.host

import com.google.gson.Gson
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.common.internal.kds.KDSErrorResponse
import com.r3.conclave.common.kds.MasterKeyType
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

class KDSTest {
    companion object {
        private const val PORT = 8091

        private val enclaveKdsConfig = EnclaveKdsConfig(
            kdsEnclaveConstraint = EnclaveConstraint.parse("S:0000000000000000000000000000000000000000000000000000000000000000 PROD:1 SEC:INSECURE"),
            persistenceKeySpec = EnclaveKdsConfig.PersistenceKeySpec(
                masterKeyType = MasterKeyType.DEVELOPMENT,
                policyConstraint = EnclaveKdsConfig.PolicyConstraint(
                    constraint = "SEC:INSECURE",
                    useOwnCodeHash = true
                )
            )
        )

        private fun mockHost(): EnclaveHost {
            return createMockHost(KDSConfiguredEnclave::class.java, null, enclaveKdsConfig)
        }

        // TODO Use Ktor instead of this internal HtttpServer API
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
            val response = Gson().toJson(kdsException).encodeToByteArray()
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
            val response = Gson().toJson(kdsException).encodeToByteArray()
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
