package com.r3.sgx.endtoendtest

import com.r3.sgx.core.common.ByteCursor
import com.r3.sgx.core.common.EncryptionInitiatingHandler
import com.r3.sgx.core.common.EncryptionProtocolId
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.attestation.AttestedOutput
import com.r3.sgx.core.common.attestation.AttestedSignatureVerifier
import com.r3.sgx.core.common.attestation.PublicKeyAttester
import com.r3.sgx.core.common.crypto.SignatureSchemeId
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.enclave.signing.SignedStuff
import com.r3.sgx.enclave.signing.Stuff
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
import org.junit.*
import org.junit.Assume.assumeTrue
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Supplier

class EndToEndTest : TestEnclavesBasedTest() {
    companion object {
        /**
         * If set to true automatic cluster setup will be skipped and must instead be initialized by running [createTestingEnvironment].
         * Use this locally to speed up testing by manually controlling cluster setup.
         */
        const val manual = false
        const val GRPC_PORT = 30080
        val log: Logger = LoggerFactory.getLogger(EndToEndTest::class.java)
        private val executor: ExecutorService = Executors.newFixedThreadPool(4)
        private val signingEnclaveMetadata = EnclaveletMetadata.read(File(System.getProperty("enclave_metadata")))

        @ClassRule
        @JvmField
        val kubernetes = KubernetesClientRule(shouldCreateFreshNamespace = !manual)

        @BeforeClass
        @JvmStatic
        fun initializeClusterIfNotManual() {
            if (!manual) {
                initializeCluster()
            }
        }

        fun initializeCluster() {
            with(kubernetes) {
                with(KubernetesClusterSetup) {
                    setupCredentials()
                    val deployments = arrayOf(
                            { deploySgxPlugin() },
                            { deployEnclaveletHost() }
                    ).map { CompletableFuture.supplyAsync(Supplier(it), executor) }
                    CompletableFuture.allOf(
                            *deployments.toTypedArray()
                    ).get()
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun shutdown() {
            executor.shutdownNow()
        }
    }

    private val diagnostic = KubernetesDiagnosticRule(kubernetes)
    private val enclaveletHost = SigningEnclaveletRule(GRPC_PORT)

    @Rule
    @JvmField
    val rules: TestRule = RuleChain.outerRule(diagnostic).around(enclaveletHost)

    @Test
    fun createTestingEnvironment() {
        assumeTrue("This is a manual test", manual)
        kubernetes.createFreshNamespace()
        initializeCluster()
        log.info("Cluster ready")
        CompletableFuture<Unit>().get()
    }

    @Test
    fun testPrebuiltSigningEnclave() {
        log.info("Getting and verifying attestation")
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
        val secureConnection = enclaveletHost
                .connectToHandler(encryptionInitiatingHandler)
                .initiate(responseHandler)

        val stuff = Stuff(1, 2.3, listOf(1, 2, 3))

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
        require(stuff == response.stuff)

        enclaveSignatureVerifier.verify(
            enclaveSignatureVerifier.decodeAttestedKey(response.key),
            response.signature,
            message)
        log.info("Done!")
    }

    private fun getAndVerifyAttestation(): AttestedOutput<ByteCursor<SgxQuote>> {
        val quoteResponses = ArrayBlockingQueue<StreamMessage<GetEpidAttestationResponse>>(2)
        enclaveletHost.getEpidAttestation(
                GetEpidAttestationRequest.getDefaultInstance(),
                QueuingStreamObserver(quoteResponses))
        val message = quoteResponses.take().cast<StreamMessage.Next<GetEpidAttestationResponse>>()
        log.info("Got attestation, verifying")
        val verification = EpidAttestationVerificationBuilder(QuoteConstraint.ValidMeasurements(signingEnclaveMetadata.measurement))
                .withAcceptDebug(true)
                .build()
        log.info("Expected measurement: ${signingEnclaveMetadata.measurement}")
        val intelPkix = verification.loadIntelPkix()
        val quote = verification.verify(intelPkix, message.next.attestation)
        log.info("Verified attestation $quote")
        return quote
    }
}
