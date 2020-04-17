package com.r3.sgx.enclavelethost.server

import com.google.protobuf.ByteString
import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxQuoteType
import com.r3.conclave.common.internal.SgxReportData
import com.r3.conclave.common.internal.attestation.AttestationParameters
import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import com.r3.sgx.core.host.EnclaveLoadMode
import com.r3.sgx.dynamictesting.EnclaveTestMode
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.enclavelethost.client.EpidAttestationVerificationBuilder
import com.r3.sgx.enclavelethost.grpc.*
import com.r3.sgx.testing.EchoHandler
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnclaveletHostTest : TestEnclavesBasedTest(mode = EnclaveTestMode.Native) {

    class EchoEnclavelet : Enclavelet() {
        override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
            return Cursor.allocate(SgxReportData)
        }

        override fun createHandler(api: EnclaveApi): Handler<*> {
            return EchoHandler()
        }
    }

    class ThrowingEnclavelet: Enclavelet() {
        class EnclaveletError: Throwable("Boom")

        override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
            return Cursor.allocate(SgxReportData)
        }

        override fun createHandler(api: EnclaveApi): Handler<*> {
            return object: Handler<Sender> {

                override fun connect(upstream: Sender) = upstream

                override fun onReceive(connection: Sender, input: ByteBuffer) {
                    System.err.println("Error")
                    throw EnclaveletError()
                }
            }
        }
    }

    companion object {
        const val port = 30011
        val serverJar: String = System.getProperty("test.hostjar")
    }

    @Test
    fun testHost() {
        withEnclaveletHost(EchoEnclavelet::class.java) {
            val channel = ManagedChannelBuilder.forTarget("localhost:$port").usePlaintext().build()
            try {
                val rpcProxy = EnclaveletHostGrpc.newStub(channel).withWaitForReady()
                val serverMessages = ArrayBlockingQueue<ServerMessage>(10)
                val requestObserver = rpcProxy.openSession(object : StreamObserver<ServerMessage> {
                    override fun onNext(value: ServerMessage) {
                        serverMessages.add(value)
                    }
                    override fun onCompleted() = Unit
                    override fun onError(t: Throwable) = Unit
                })
                requestObserver.onNext(ClientMessage.newBuilder()
                        .setBlob(ByteString.copyFromUtf8("Hello"))
                        .build()
                )
                val message1 = serverMessages.take()
                assertEquals("Hello", message1.blob.toStringUtf8())
                requestObserver.onCompleted()
            } finally {
                channel.gracefulShutdown()
            }
        }
    }

    @Test
    fun retrieveSimulatedAttestationReport() {
        withEnclaveletHost(EchoEnclavelet::class.java) {
            val channel = ManagedChannelBuilder.forTarget("localhost:$port").usePlaintext().build()
            var throwable: Throwable? = null
            try {
                val proxy = EnclaveletHostGrpc.newStub(channel).withWaitForReady()
                val serverMessages = ArrayBlockingQueue<GetEpidAttestationResponse>(10)
                proxy.getEpidAttestation(GetEpidAttestationRequest.getDefaultInstance(),
                        object : StreamObserver<GetEpidAttestationResponse> {
                            override fun onNext(value: GetEpidAttestationResponse) {
                                serverMessages.add(value)
                            }

                            override fun onCompleted() {
                            }

                            override fun onError(t: Throwable?) {
                                println("Received exception from server: $t")
                                throwable = t
                                serverMessages.put(GetEpidAttestationResponse.getDefaultInstance())
                            }
                        })
                assertNull(throwable)
                val epidAttestation = serverMessages.take()
                val attestationVerifier = EpidAttestationVerificationBuilder()
                        .withAcceptDebug(true)
                        .build()
                attestationVerifier.verify(AttestationParameters.MOCK, epidAttestation.attestation)
            } finally {
                channel.gracefulShutdown()
            }
        }
    }

    @Test
    fun testHostTerminationOnUnhandledException() {
        withEnclaveletHost(ThrowingEnclavelet::class.java) { host ->
            val channel = ManagedChannelBuilder.forTarget("localhost:$port").usePlaintext().build()
            val gotError = CountDownLatch(1)
            try {
                val rpcProxy = EnclaveletHostGrpc.newStub(channel).withWaitForReady()
                val requestObserver = rpcProxy.openSession(object : StreamObserver<ServerMessage> {
                    override fun onNext(value: ServerMessage?) {
                    }

                    override fun onCompleted() {
                    }

                    override fun onError(t: Throwable?) {
                        System.err.println("Received error from host $t")
                        gotError.countDown()
                    }
                })
                requestObserver.onNext(ClientMessage.newBuilder()
                        .setBlob(ByteString.copyFromUtf8("Ping"))
                        .build())
                requestObserver.onCompleted()
                gotError.await()
                assertTrue { host.waitFor(10000, TimeUnit.MILLISECONDS) }
            } finally {
                channel.gracefulShutdown()
            }
        }
    }

    @Test
    fun testConfigParsing() {
        val configFile = javaClass.classLoader.getResource("test-config.yml")!!
        val config = (EnclaveletHostConfiguration.read(File(configFile.toURI())))
        val expected = EnclaveletHostConfiguration(
                bindPort=8080,
                threadPoolSize=8,
                epidSpid= "***REMOVED***",
                iasSubscriptionKey = "***REMOVED***",
                epidQuoteType= SgxQuoteType.LINKABLE,
                attestationServiceUrl = "xxx",
                enclaveLoadMode = EnclaveLoadMode.RELEASE,
                mockAttestationServiceInSimulation = true)
        assertEquals(expected, config)
    }

    @Test
    fun testConfigParsingWithDefaults() {
        val configFile = javaClass.classLoader.getResource("test-config-overrides.yml")!!
        val config = (EnclaveletHostConfiguration.readWithDefaults(File(configFile.toURI())))
        val expected = EnclaveletHostConfiguration(
                bindPort = 8080,
                threadPoolSize = 8,
                epidSpid = "***REMOVED***",
                iasSubscriptionKey = "***REMOVED***",
                epidQuoteType = SgxQuoteType.LINKABLE,
                attestationServiceUrl = "xxx",
                enclaveLoadMode = EnclaveLoadMode.DEBUG,
                mockAttestationServiceInSimulation = true)
        assertEquals(expected, config)
    }

    private fun getTmpFileNames(): String {
        return File(System.getProperty("java.io.tmpdir"))
                .listFiles()!!
                .sorted()
                .joinToString(",") { it.path }
    }

    private fun withEnclaveletHost(enclaveClass: Class<out Enclavelet>, block: (Process) -> Unit) {
        val enclaveFile = testEnclaves.getEnclave(enclaveClass)
        val config = javaClass.classLoader.getResource("test-config.yml")!!
        val tmpFilesBefore = getTmpFileNames()
        val host = ProcessBuilder().
                command(
                        "java",
                        "-jar", serverJar,
                        "-p", port.toString(),
                        "-m", "SIMULATION",
                        "-c", config.path,
                        enclaveFile.absolutePath,
                        enclaveClass.name
                )
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start()
        try {
            block(host)
        } finally {
            if (host.isAlive) {
                host.destroy()
                host.waitFor()
            }
            assertEquals(tmpFilesBefore, getTmpFileNames())
        }
    }

    private fun ManagedChannel.gracefulShutdown(seconds: Long = 5) {
        shutdown()
        if (!awaitTermination(seconds, TimeUnit.SECONDS)) {
            shutdownNow()
        }
    }
}
