package com.r3.conclave.host.web

import com.google.gson.*
import com.r3.conclave.common.EnclaveException
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.internal.createMockHost
import com.r3.conclave.internaltesting.threadWithFuture
import com.r3.conclave.mail.EnclaveMail
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.EntityBuilder
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.web.server.LocalServerPort
import java.io.IOException
import java.security.PublicKey
import java.util.*

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EnclaveWebControllerTest {
    @LocalServerPort
    private var serverPort = 0
    // EnclaveWebController uses the EnclaveHost.load overload which scans the classpath for a single enclave. This
    // test assumes there is only one such enclave on the test classpath for conclave-web-host, namely TestEnclave
    // defined below. If this changes and conclave-web-host needs multiple enclaves on the test classpath then
    // EnclaveWebController should probably have an internal parameter to load that enclave. The default behaviour of
    // EnclaveWebController must still remain to scan the classpath to ensure a good user experience.
    @Autowired
    private lateinit var controller: EnclaveWebController
    private lateinit var client: MockClient

    private val httpClient = HttpClients.createDefault()

    @BeforeEach
    fun init() {
        client = MockClient()
    }

    @AfterEach
    fun close() {
        httpClient.close()
    }

    @Test
    fun attestation() {
        val firstEii = enclaveHost.enclaveInstanceInfo
        assertThat(downloadEnclaveInstanceInfo()).isEqualTo(firstEii)
        enclaveHost.updateAttestation()
        val secondEii = enclaveHost.enclaveInstanceInfo
        assertThat(secondEii).isNotEqualTo(firstEii)
        // Make sure any updates are immediately available.
        assertThat(downloadEnclaveInstanceInfo()).isEqualTo(secondEii)
    }

    @Test
    fun `delivering mail with no synchronous response`() {
        val requestBytes = client.postOffice.encryptMail("Hello".toByteArray())
        val response = httpDeliverMail(requestBytes, client.correlationId)
        assertThat(response).isEmpty()
    }

    @Test
    fun `delivering mail with synchronous response`() {
        assertThat(client.deliverMail("ping")).isEqualTo("pong")
    }

    @Test
    fun `delivering mail with asynchronous response`() {
        // You need another client to trigger an asynchronous response.
        val client2 = MockClient()
        // Send the payload that we want echoed back asynchronously
        client.deliverMail("Hello")
        // Use the second client to trigger it.
        client2.deliverMail("previous")
        // Check there's no mix-up and that the second client can't receive it.
        assertThat(client2.pollMail()).isNull()
        assertThat(client.pollMail()).isEqualTo("Hello")
        // Check that polling removes it.
        assertThat(client.pollMail()).isNull()
    }

    @Test
    fun `if enclave responses back with two mails then the second becomes asynchronous`() {
        assertThat(client.deliverMail("two")).isEqualTo("first")
        assertThat(client.pollMail()).isEqualTo("second")
    }

    @Test
    fun `multiple concurrent clients with asynchronous responses buffered`() {
        val clients = Array(10) { MockClient() }

        clients.map { client ->
            threadWithFuture {
                // This should create two buffered async responses per client
                assertThat(client.deliverMail("two")).isEqualTo("first")
                assertThat(client.deliverMail("two")).isEqualTo("first")
            }
        }.forEach { it.join() }

        for (client in clients) {
            assertThat(client.pollMail()).isEqualTo("second")
            assertThat(client.pollMail()).isEqualTo("second")
            assertThat(client.pollMail()).isNull()
        }
    }

    @Test
    fun `delivery of undecryptable mail`() {
        val anotherEnclavePostOffice = run {
            val anotherEnclave = createMockHost(TestEnclave::class.java)
            anotherEnclave.start(null, null, null) { }
            anotherEnclave.enclaveInstanceInfo.createPostOffice()
        }

        val response = httpDeliverMail(
            mailBytes = anotherEnclavePostOffice.encryptMail("Hello?".toByteArray()),
            correlationId = "id",
            expectedStatusCode = HttpStatus.SC_BAD_REQUEST,
            expectedContentType = ContentType.APPLICATION_JSON
        )

        val jsonResponse = JsonParser.parseString(response.decodeToString()).asJsonObject
        assertThat(jsonResponse["error"]?.asString).isEqualTo("MAIL_DECRYPTION")
    }

    @Test
    fun `enclave throws exception`() {
        val response = httpDeliverMail(
            mailBytes = client.postOffice.encryptMail("throw".toByteArray()),
            correlationId = "id",
            expectedStatusCode = HttpStatus.SC_BAD_REQUEST,
            expectedContentType = ContentType.APPLICATION_JSON
        )


        val jsonResponse = JsonParser.parseString(response.decodeToString()).asJsonObject
        assertThat(jsonResponse["error"]?.asString).isEqualTo("ENCLAVE_EXCEPTION")
        assertThat(jsonResponse["message"]?.asString).isEqualTo("bang!")  // This is only available for non-release enclaves
    }

    private fun downloadEnclaveInstanceInfo(): EnclaveInstanceInfo {
        return httpClient.execute(HttpGet(url("attestation"))).use {
            assertThat(it.code).isEqualTo(HttpStatus.SC_OK)
            assertThat(it.entity.contentType).isEqualTo(ContentType.APPLICATION_OCTET_STREAM.toString())
            EnclaveInstanceInfo.deserialize(EntityUtils.toByteArray(it.entity))
        }
    }

    private fun httpDeliverMail(
        mailBytes: ByteArray,
        correlationId: String,
        expectedStatusCode: Int = HttpStatus.SC_OK,
        expectedContentType: ContentType = ContentType.APPLICATION_OCTET_STREAM
    ): ByteArray {
        val httpPost = HttpPost(url("deliver-mail")).apply {
            addHeader("Correlation-ID", correlationId)
            entity = EntityBuilder.create().setBinary(mailBytes).build()
        }
        return httpClient.execute(httpPost).use { response ->
            if (response.code != expectedStatusCode) {
                throw IOException(EntityUtils.toString(response.entity))
            }
            assertThat(response.entity.contentType).isEqualTo(expectedContentType.mimeType)
            EntityUtils.toByteArray(response.entity)
        }
    }

    private fun httpPollMail(correlationId: String): ByteArray {
        val httpPost = HttpPost(url("poll-mail")).apply {
            addHeader("Correlation-ID", correlationId)
        }
        return httpClient.execute(httpPost).use {
            assertThat(it.code).isEqualTo(HttpStatus.SC_OK)
            assertThat(it.entity.contentType).isEqualTo(ContentType.APPLICATION_OCTET_STREAM.toString())
            EntityUtils.toByteArray(it.entity)
        }
    }

    private fun url(endPoint: String): String = "http://localhost:$serverPort/$endPoint"

    private val enclaveHost: EnclaveHost get() = controller.enclaveHostService.enclaveHost

    class TestEnclave : Enclave() {
        private lateinit var previousRequest: String
        private lateinit var previousSender: PublicKey
        private var previousRoutingHint: String? = null

        override fun receiveMail(mail: EnclaveMail, routingHint: String?) {
            val request = String(mail.bodyAsBytes)
            if (request == "ping") {
                postMail(postOffice(mail).encryptMail("pong".toByteArray()), routingHint)
            } else if (request == "previous") {
                postMail(postOffice(previousSender).encryptMail(previousRequest.toByteArray()), previousRoutingHint)
            } else if (request == "two") {
                postMail(postOffice(mail).encryptMail("first".toByteArray()), routingHint)
                postMail(postOffice(mail).encryptMail("second".toByteArray()), routingHint)
            } else if (request == "throw") {
                throw EnclaveException("bang!")
            }
            previousRequest = request
            previousSender = mail.authenticatedSender
            previousRoutingHint = routingHint
        }
    }

    private inner class MockClient {
        val postOffice = enclaveHost.enclaveInstanceInfo.createPostOffice()
        val correlationId = UUID.randomUUID().toString()

        fun deliverMail(body: String): String? {
            val mailBytes = postOffice.encryptMail(body.toByteArray())
            val responseBytes = httpDeliverMail(mailBytes, correlationId)
            // Empty bytes means no response
            if (responseBytes.isEmpty()) return null
            val response = postOffice.decryptMail(responseBytes)
            return String(response.bodyAsBytes)
        }

        fun pollMail(): String? {
            val responseBytes = httpPollMail(correlationId)
            // Empty bytes means no response
            if (responseBytes.isEmpty()) return null
            val response = postOffice.decryptMail(responseBytes)
            return String(response.bodyAsBytes)
        }
    }
}
