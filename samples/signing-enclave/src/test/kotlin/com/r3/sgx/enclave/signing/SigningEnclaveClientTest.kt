package com.r3.sgx.enclave.signing

import com.r3.conclave.host.internal.AttestationParameters
import com.r3.sgx.core.common.ByteCursor
import com.r3.sgx.core.common.EncryptionInitiatingHandler
import com.r3.sgx.core.common.EncryptionProtocolId
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.attestation.AttestedOutput
import com.r3.sgx.core.common.attestation.AttestedSignatureVerifier
import com.r3.sgx.core.common.attestation.PublicKeyAttester
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.enclave.signing.internal.MyAMQPSerializationScheme
import com.r3.sgx.enclave.signing.internal.asContextEnv
import com.r3.sgx.enclavelethost.client.EnclaveletMetadata
import com.r3.sgx.enclavelethost.client.EpidAttestationVerificationBuilder
import com.r3.sgx.enclavelethost.client.QuoteConstraint
import com.r3.sgx.enclavelethost.grpc.GetEpidAttestationRequest
import com.r3.sgx.enclavelethost.grpc.GetEpidAttestationResponse
import com.r3.sgx.testing.*
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import kotlin.test.fail

class SigningEnclaveClientTest {
    private companion object {
        private val grpcPort: Int = getIntegerProperty("com.r3.sgx.enclave.simulated.grpc.port")
        private val log: Logger = LoggerFactory.getLogger(SigningEnclaveClientTest::class.java)
        private val signingEnclaveMetadata = EnclaveletMetadata.read(File(getStringProperty("com.r3.sgx.enclave_metadata")))

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            log.info("Enclave Host Port: {}", grpcPort)
        }

        private fun getIntegerProperty(name: String): Int = Integer.getInteger(name) ?: fail("Missing system property '$name'")
        private fun getStringProperty(name: String): String = System.getProperty(name) ?: fail("Missing system property '$name'")
    }

    @get:Rule
    val enclavelet = SigningEnclaveletRule(grpcPort)

    @Test
    fun testSigningEnclave() {
        val attestedQuote = getAndVerifyAttestation()
        val keyAuthenticator = PublicKeyAttester(attestedQuote)
        val enclaveSignatureVerifier = AttestedSignatureVerifier(
                SignatureSchemeId.EDDSA_ED25519_SHA512,
                keyAuthenticator)
        val encryptionInitiatingHandler = EncryptionInitiatingHandler(
                signatureVerifier = enclaveSignatureVerifier,
                protocolId = EncryptionProtocolId.ED25519_AESGCM128)

        log.info("Requesting signature")
        val responseHandler = BlockingBytesRecordingHandler()
        val secureConnection = enclavelet
                .connectToHandler(encryptionInitiatingHandler)
                .initiate(responseHandler)

        val stuff = Stuff(0x0BadF00d, 2.3, listOf(1, 2, 3))

        val message = MyAMQPSerializationScheme.createSerializationEnv().asContextEnv {
            stuff.serialize(/*context = SerializationDefaults.P2P_CONTEXT*/)
        }.bytes

        secureConnection.send(ByteBuffer.wrap(message))

        log.info("Waiting for response")
        val responseBytes = responseHandler.received.take()
        log.info("Checking response")
        val response = MyAMQPSerializationScheme.createSerializationEnv().asContextEnv {
            SerializedBytes<SignedStuff>(responseBytes).deserialize()
        }
        assertEquals(stuff, response.stuff)

        enclaveSignatureVerifier.verify(
            enclaveSignatureVerifier.decodeAttestedKey(response.key),
            response.signature,
            message)

        log.info("Done!")
    }

    private fun getAndVerifyAttestation(): AttestedOutput<ByteCursor<SgxQuote>> {
        val quoteResponses = ArrayBlockingQueue<StreamMessage<GetEpidAttestationResponse>>(2)
        enclavelet.getEpidAttestation(
                GetEpidAttestationRequest.getDefaultInstance(),
                QueuingStreamObserver(quoteResponses))
        val message = quoteResponses.take().cast<StreamMessage.Next<GetEpidAttestationResponse>>()
        log.info("Got attestation, verifying")
        val verification = EpidAttestationVerificationBuilder(QuoteConstraint.ValidMeasurements(signingEnclaveMetadata.measurement))
                .withAcceptDebug(true)
                .build()
        log.info("Expected measurement: ${signingEnclaveMetadata.measurement}")
        val quote = verification.verify(AttestationParameters.MOCK, message.next.attestation)
        log.info("Verified attestation $quote")
        return quote
    }
}
