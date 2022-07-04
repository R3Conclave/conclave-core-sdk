package com.r3.conclave.integrationtests.general.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.client.PostOfficeBuilder
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.InvalidEnclaveException
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.kds.KDSConfiguration
import com.r3.conclave.integrationtests.general.common.tasks.EnclaveTestAction
import com.r3.conclave.integrationtests.general.common.tasks.Increment
import com.r3.conclave.integrationtests.general.common.tasks.decode
import com.r3.conclave.integrationtests.general.common.tasks.encode
import com.r3.conclave.integrationtests.general.common.threadWithFuture
import com.r3.conclave.integrationtests.general.commontest.TestKds
import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.mail.PostOffice
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.util.*

class KdsMailTests {
    companion object {
        private lateinit var kdsUrl: URL

        const val VALID_POLICY_CONSTRAINT =
            "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:1 SEC:INSECURE"
        const val INVALID_POLICY_CONSTRAINT =
            "S:DF229F35094D9AC237EF23287156F278902579BCD486FA28FA66E87BB1ADBAD2 PROD:2 SEC:INSECURE"
        val VALID_KDS_ENCLAVE_CONSTRAINT =
            EnclaveConstraint.parse("S:B4CDF6F4FA5B484FCA82292CE340FF305AA294F19382178BEA759E30E7DCFE2D PROD:1 SEC:INSECURE")
        val INVALID_KDS_ENCLAVE_CONSTRAINT =
            EnclaveConstraint.parse("S:B4CDF6F4FA5B484FCA82292CE340FF305AA294F19382178BEA759E30E7DCFE2D PROD:2 SEC:INSECURE")

        private val validPublicKey = Curve25519PublicKey(
            Base64.getDecoder().decode("d/DKtI1HcZg0QaTmW56+alpvZNANnCtyX3cJtpYLGk4=")
        )

        @BeforeAll
        @JvmStatic
        fun startKds() {
            kdsUrl = URL("http://localhost:${TestKds.testKdsPort}")
        }
    }

    // Capture any response mail the enclave produces, which are produced by the callback provided to EnclaveHost.start.
    // We need to use a ThreadLocal since mail may be sent into the enclave on multiple threads.
    private val mailResponseTheadLocal = ThreadLocal<ByteArray>()
    private lateinit var enclaveHost: EnclaveHost

    @BeforeEach
    fun startEnclave() {
        enclaveHost = EnclaveHost.load("com.r3.conclave.integrationtests.general.threadsafeenclave.ThreadSafeEnclave")
        val attestationParameters = TestUtils.getAttestationParams(enclaveHost)
        enclaveHost.start(attestationParameters, null, null, KDSConfiguration(kdsUrl.toString())) { commands ->
            for (command in commands) {
                if (command is MailCommand.PostMail) {
                    check(mailResponseTheadLocal.get() == null) {
                        "This test class currently only supports a single mail response per mail request"
                    }
                    mailResponseTheadLocal.set(command.encryptedBytes)
                }
            }
        }
    }

    @AfterEach
    fun close() {
        if (::enclaveHost.isInitialized) {
            enclaveHost.close()
        }
    }

    @Test
    fun `PostOfficeBuilder-usingKDS happy path`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)
        val postOffice = PostOfficeBuilder.usingKDS(kdsUrl, kdsSpec, VALID_KDS_ENCLAVE_CONSTRAINT).build()

        val result = deliverKdsMail(postOffice, Increment(1))
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `PostOfficeBuilder-usingKDS with invalid policy constraint`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, INVALID_POLICY_CONSTRAINT)
        val postOffice = PostOfficeBuilder.usingKDS(kdsUrl, kdsSpec, VALID_KDS_ENCLAVE_CONSTRAINT).build()

        assertThatThrownBy { deliverKdsMail(postOffice, Increment(1)) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("The application does not meet the required key policy.")
    }

    @Test
    fun `PostOfficeBuilder-usingKDS with invalid KDS enclave constraint`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)

        assertThatThrownBy { PostOfficeBuilder.usingKDS(kdsUrl, kdsSpec, INVALID_KDS_ENCLAVE_CONSTRAINT) }
            .isInstanceOf(InvalidEnclaveException::class.java)
            .hasMessageContaining("Enclave has a product ID of 1 which does not match the criteria of 2")
    }

    @Test
    fun `PostOfficeBuilder-usingKDSPublicKey happy path`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)
        val postOffice: PostOffice = PostOfficeBuilder.usingKDSPublicKey(validPublicKey, kdsSpec).build()
        val result = deliverKdsMail(postOffice, Increment(1))
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `PostOfficeBuilder-usingKDSPublicKey with invalid policy constraint`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, INVALID_POLICY_CONSTRAINT)
        val postOffice: PostOffice = PostOfficeBuilder.usingKDSPublicKey(validPublicKey, kdsSpec).build()

        assertThatThrownBy { deliverKdsMail(postOffice, Increment(1)) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("The application does not meet the required key policy.")
    }

    @Test
    fun `PostOfficeBuilder-usingKDSPublicKey with incorrect key spec`() {
        val incorrectKeySpec = KDSKeySpec("myNewSpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)
        val postOffice: PostOffice = PostOfficeBuilder.usingKDSPublicKey(validPublicKey, incorrectKeySpec).build()

        assertThatThrownBy {
            deliverKdsMail(postOffice, Increment(1))
        }.isInstanceOf(MailDecryptionException::class.java)
    }

    @Test
    fun `PostOfficeBuilder-usingKDSResponse happy path`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)
        val kdsPublicKeyResponseStream = makePublicKeyRequest(kdsSpec)
        val postOffice: PostOffice = PostOfficeBuilder.usingKDSResponse(
            kdsPublicKeyResponseStream,
            kdsSpec,
            VALID_KDS_ENCLAVE_CONSTRAINT
        ).build()
        val result = deliverKdsMail(postOffice, Increment(1))
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `PostOfficeBuilder-usingKDSResponse with invalid policy constraint`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, INVALID_POLICY_CONSTRAINT)
        val kdsPublicKeyResponseStream = makePublicKeyRequest(kdsSpec)
        val postOffice: PostOffice = PostOfficeBuilder.usingKDSResponse(
            kdsPublicKeyResponseStream,
            kdsSpec,
            VALID_KDS_ENCLAVE_CONSTRAINT
        ).build()

        assertThatThrownBy { deliverKdsMail(postOffice, Increment(1)) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("The application does not meet the required key policy.")
    }

    @Test
    fun `PostOfficeBuilder-usingKDSResponse with invalid KDS enclave constraint`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)
        val kdsPublicKeyResponseStream = makePublicKeyRequest(kdsSpec)

        assertThatThrownBy {
            PostOfficeBuilder.usingKDSResponse(
                kdsPublicKeyResponseStream,
                kdsSpec,
                INVALID_KDS_ENCLAVE_CONSTRAINT
            )
        }.isInstanceOf(InvalidEnclaveException::class.java)
            .hasMessageContaining("Enclave has a product ID of 1 which does not match the criteria of 2")
    }

    @Test
    fun `PostOfficeBuilder-usingKDSResponse with invalid request`() {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)

        val jsonBody = mapOf(
            "name" to "mySpec",
            "masterKeyType" to "not a master key",
            "policyConstraint" to VALID_POLICY_CONSTRAINT
        )

        val kdsPublicKeyResponseStream = makePublicKeyRequest(jsonBody)

        assertThatThrownBy {
            PostOfficeBuilder.usingKDSResponse(
                kdsPublicKeyResponseStream,
                kdsSpec,
                VALID_KDS_ENCLAVE_CONSTRAINT
            )
        }.isInstanceOf(IOException::class.java)
            .hasMessageContaining("The requested master key type is not supported")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `sending two KDS encrypted mail`(sameTopic: Boolean) {
        val kdsSpec = KDSKeySpec("mySpec", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT)
        val kdsPostOfficeBuilder = PostOfficeBuilder.usingKDS(kdsUrl, kdsSpec, VALID_KDS_ENCLAVE_CONSTRAINT)
        val firstPostOffice = kdsPostOfficeBuilder.setTopic("first").build()
        val secondPostOffice = if (sameTopic) firstPostOffice else kdsPostOfficeBuilder.setTopic("second").build()

        assertThat(deliverKdsMail(firstPostOffice, Increment(1))).isEqualTo(2)
        assertThat(deliverKdsMail(secondPostOffice, Increment(2))).isEqualTo(3)
    }

    @Test
    fun `sending multiple KDS encrypted mail concurrently`() {
        // This test requires a thread-safe enclave. The enclave name doesn't obviously prove that but it's good enough.
        assertThat(enclaveHost.enclaveClassName).contains("ThreadSafeEnclave")

        val futures = (1..10).map { index ->
            // Create 10 concurrent streams, within each having 10 sequential mail
            threadWithFuture {
                val postOffice = PostOfficeBuilder.usingKDS(
                    kdsUrl,
                    KDSKeySpec("key-spec-$index", MasterKeyType.DEVELOPMENT, VALID_POLICY_CONSTRAINT),
                    VALID_KDS_ENCLAVE_CONSTRAINT
                ).build()
                repeat(10) { payload ->
                    assertThat(deliverKdsMail(postOffice, Increment(payload))).isEqualTo(payload + 1)
                }
            }
        }
        futures.map { it.join() }
    }

    private fun makePublicKeyRequest(keySpec: KDSKeySpec): InputStream {
        val jsonBody = mapOf(
            "name" to keySpec.name,
            "masterKeyType" to keySpec.masterKeyType,
            "policyConstraint" to keySpec.policyConstraint
        )
        return makePublicKeyRequest(jsonBody)
    }

    private fun makePublicKeyRequest(jsonBody: Map<String, Any>): InputStream {
        val request = HttpRequest.newBuilder()
            .uri(kdsUrl.toURI().resolve("/public"))
            .header("API-VERSION", "1")
            .header("Content-Type", "application/json; utf-8")
            .POST(BodyPublishers.ofString(ObjectMapper().writeValueAsString(jsonBody)))
            .build()
        val httpClient = HttpClient.newHttpClient()
        val response: HttpResponse<InputStream> = httpClient.send(request, BodyHandlers.ofInputStream())
        return response.body()
    }

    private fun <R> deliverKdsMail(kdsPostOffice: PostOffice, action: EnclaveTestAction<R>): R? {
        val encodedAction = encode(EnclaveTestAction.serializer(action.resultSerializer()), action)
        val encryptedMail: ByteArray = kdsPostOffice.encryptMail(encodedAction)
        enclaveHost.deliverMail(encryptedMail, null)
        val mailResponseBytes = mailResponseTheadLocal.get()
        return if (mailResponseBytes == null) {
            null
        } else {
            mailResponseTheadLocal.remove()
            val decryptedMailResponse = kdsPostOffice.decryptMail(mailResponseBytes)
            return decode(action.resultSerializer(), decryptedMailResponse.bodyAsBytes)
        }
    }
}
