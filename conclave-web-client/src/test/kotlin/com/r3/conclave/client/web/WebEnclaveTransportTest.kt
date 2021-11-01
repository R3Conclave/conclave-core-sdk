package com.r3.conclave.client.web

import com.r3.conclave.client.EnclaveClient
import com.r3.conclave.client.EnclaveTransport
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.EnclaveException
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.mail.internal.noise.protocol.Noise
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.network.tls.certificates.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Path
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.io.path.div


/**
 * Make sure [WebEnclaveTransport] behaves according to the enclave host web server REST spec. Thus most of these tests
 * don't actually need an enclave or real mail.
 */
class WebEnclaveTransportTest {
    private val enclaveHost = createMockHost(NoOpEnclave::class.java)

    private lateinit var server: ApplicationEngine
    private lateinit var transport: WebEnclaveTransport
    private lateinit var connection: EnclaveTransport.ClientConnection

    @BeforeEach
    fun init() {
        enclaveHost.start(null, null, null) { }

        val randomPort = ServerSocket(0).use { it.localPort }  // TODO Fix https://youtrack.jetbrains.com/issue/KTOR-686
        server = embeddedServer(Netty, port = randomPort) {
            install(ContentNegotiation) {
                json()
            }
        }
        server.start()

        transport = WebEnclaveTransport("http://localhost:${server.environment.connectors[0].port}")
        connection = transport.connect(EnclaveClient(EnclaveConstraint()))
    }

    @AfterEach
    fun close() {
        if (::connection.isInitialized) {
            connection.disconnect()
        }
        if (::transport.isInitialized) {
            transport.close()
        }
        if (::server.isInitialized) {
            server.stop(0, 0)
        }
        enclaveHost.close()
    }

    @Test
    fun attestation() {
        server.application.install(Routing) {
            get("/attestation") {
                call.respondBytes(enclaveHost.enclaveInstanceInfo.serialize())
            }
        }

        assertThat(transport.enclaveInstanceInfo()).isEqualTo(enclaveHost.enclaveInstanceInfo)
    }

    @Test
    fun `sendMail with no response`() {
        server.application.install(Routing) {
            post("/deliver-mail") {
                call.respondBytes(ByteArray(0))
            }
        }

        val responseBytes = sendFakeMail()
        assertThat(responseBytes).isNull()
    }

    @Test
    fun `sendMail with response`() {
        val response = "This is a response!".toByteArray()

        server.application.install(Routing) {
            post("/deliver-mail") {
                call.respondBytes(response)
            }
        }

        val responseBytes = sendFakeMail()
        assertThat(responseBytes?.let(::String)).isEqualTo("This is a response!")
    }

    @Test
    fun `sendMail where server responds back with decryption error`() {
        server.application.install(Routing) {
            post("/deliver-mail") {
                call.respond(BadRequest, ErrorResponse("MAIL_DECRYPTION", "blah blah"))
            }
        }

        assertThatThrownBy { sendFakeMail() }
            .isInstanceOf(MailDecryptionException::class.java)
            .hasMessage("blah blah")
    }

    @Test
    fun `sendMail where server responds back with exception from 'enclave'`() {
        server.application.install(Routing) {
            post("/deliver-mail") {
                call.respond(BadRequest, ErrorResponse("ENCLAVE_EXCEPTION", "there is no enclave!"))
            }
        }

        assertThatThrownBy { sendFakeMail() }
            .isInstanceOf(EnclaveException::class.java)
            .hasMessage("there is no enclave!")
    }

    @Test
    fun `sendMail where server responds back with invalid error response`() {
        server.application.install(Routing) {
            post("/deliver-mail") {
                call.respond(BadRequest, "Not Json")
            }
        }

        assertThatThrownBy { sendFakeMail() }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("Not Json")
    }

    @Test
    fun `sendMail where server responds back with unknown error`() {
        server.application.install(Routing) {
            post("/deliver-mail") {
                call.respond(BadRequest, ErrorResponse("HYPOTHETICAL_NEW_ERROR", "message"))
            }
        }

        assertThatThrownBy { sendFakeMail() }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("HYPOTHETICAL_NEW_ERROR")
    }

    @Test
    fun `pollMail returns no mail`() {
        server.application.install(Routing) {
            post("/poll-mail") {
                call.respondBytes(ByteArray(0))
            }
        }

        assertThat(connection.pollMail()).isNull()
    }

    @Test
    fun `pollMail returns mail`() {
        val response = "I am (not) mail!".toByteArray()

        server.application.install(Routing) {
            post("/poll-mail") {
                call.respondBytes(response)
            }
        }

        val responseBytes = connection.pollMail()
        assertThat(responseBytes?.let(::String)).isEqualTo("I am (not) mail!")
    }

    @Test
    fun `each connection uses a unique correlation ID`() {
        val correlationIdsUsed = ConcurrentHashMap.newKeySet<String?>()

        server.application.install(Routing) {
            post("/deliver-mail") {
                correlationIdsUsed += call.request.header("Correlation-ID")
                call.respondBytes(ByteArray(0))
            }
            post("/poll-mail") {
                correlationIdsUsed += call.request.header("Correlation-ID")
                call.respondBytes(ByteArray(0))
            }
        }

        val connection2 = transport.connect(EnclaveClient(EnclaveConstraint()))

        listOf(connection, connection2).forEachIndexed { index, clientConnection ->
            clientConnection.sendMail(ByteArray(16).also(Noise::random))
            assertThat(correlationIdsUsed).doesNotContainNull()
            assertThat(correlationIdsUsed).hasSize(index + 1)
            clientConnection.pollMail()
            assertThat(correlationIdsUsed).hasSize(index + 1)
        }
    }

    @Test
    fun `custom SSLContext`(@TempDir dir: Path) {
        // Create a self-signed certificate
        val serverKeyStore = generateCertificate(
            file = (dir / "keystore.jks").toFile(),
            keyAlias = "alias",
            keyPassword = "password",
            jksPassword = "password"
        )

        val randomPort = ServerSocket(0).use { it.localPort }  // TODO Fix https://youtrack.jetbrains.com/issue/KTOR-686
        val environment = applicationEngineEnvironment {
            sslConnector(
                keyStore = serverKeyStore,
                keyAlias = "alias",
                keyStorePassword = { "password".toCharArray() },
                privateKeyPassword = { "password".toCharArray() }
            ) {
                port = randomPort
            }
            module {
                routing {
                    get("/attestation") {
                        call.respondBytes(enclaveHost.enclaveInstanceInfo.serialize())
                    }
                }
            }
        }
        val sslServer = embeddedServer(Netty, environment).start()

        // Copy the server's certificate into the client's trust store.
        val clientTrustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        clientTrustStore.load(null)
        clientTrustStore.setCertificateEntry("alias", serverKeyStore.getCertificate("alias"))

        val sslContext = clientTrustStore.toSSLContext()
        val sslTransport = WebEnclaveTransport("https://localhost:$randomPort", Duration.ofMinutes(1), sslContext)
        assertThat(sslTransport.enclaveInstanceInfo()).isEqualTo(enclaveHost.enclaveInstanceInfo)

        sslTransport.close()
        sslServer.stop(0, 0)
    }

    private fun sendFakeMail(): ByteArray? {
        return connection.sendMail(ByteArray(16).also(Noise::random))
    }

    private fun KeyStore.toSSLContext(): SSLContext {
        val trustManagerFactory = TrustManagerFactory.getInstance("X509")
        trustManagerFactory.init(this)
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }
    }

    @Serializable
    class ErrorResponse(val error: String?, val message: String?)

    private class NoOpEnclave : Enclave()
}
