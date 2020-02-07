package com.r3.sgx.multiplex.enclave

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.EnclaveletHostHandler
import com.r3.sgx.core.host.EpidAttestationHostConfiguration
import com.r3.sgx.dynamictesting.TestEnclaves
import com.r3.sgx.enclavelethost.grpc.ClientMessage
import com.r3.sgx.enclavelethost.grpc.EnclaveletHostGrpc.*
import com.r3.sgx.enclavelethost.grpc.ServerMessage
import com.r3.sgx.multiplex.client.MultiplexClientHandler
import com.r3.sgx.testing.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExpectedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.*
import java.util.function.Consumer

class MultiplexEnclaveClientTest {
    private companion object {
        private val grpcPort: Int = mandatoryIntProperty("com.r3.sgx.enclave.simulated.grpc.port")
        private val log: Logger = LoggerFactory.getLogger(MultiplexEnclaveClientTest::class.java)
        const val MESSAGE = "She sells sea shells on the sea shore."
        const val OTHER_MESSAGE = "The Sheik's sixth sheep is sick."

        val attestationConfiguration = EpidAttestationHostConfiguration(
            quoteType = SgxQuoteType32.LINKABLE,
            spid = Cursor.allocate(SgxSpid)
        )

        fun createDynamicEnclaveletHostHandler() = EnclaveletHostHandler(attestationConfiguration)

        @JvmField
        @ClassRule
        val testEnclaves = TestEnclaves()

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            log.info("Enclave Host Port: {}", grpcPort)
        }
    }

    private lateinit var channel: ManagedChannel
    private lateinit var clientSession: EnclaveletClient.Session<MultiplexClientHandler.Connection>

    @get:Rule
    val exception: ExpectedException = ExpectedException.none()

    @Before
    fun setup() {
        channel = ManagedChannelBuilder.forTarget("localhost:$grpcPort")
            .usePlaintext()
            .build()
        val enclaveletHost = newStub(channel)
            .withCompression("gzip")
            .withWaitForReady()
        val enclaveletClient = EnclaveletClient(enclaveletHost)
        clientSession = enclaveletClient.addDownstream(MultiplexClientHandler(), queueSize = 10)
    }

    @After
    fun done() {
        if (::channel.isInitialized) {
            channel.shutdownNow()
        }
    }

    @Test
    fun testMultiplexEnclave() {
        val multiplexChannel = clientSession.connection

        val shouterJar = testEnclaves.getEnclaveJar(ShoutingEnclavelet::class.java)
        val enclaveFuture = multiplexChannel.loader.sendJar(shouterJar.toByteBuffer())

        assertEquals(1, clientSession.awaitResponseFor(enclaveFuture, seconds = 10))
        assertFalse(enclaveFuture.isCompletedExceptionally)
        assertTrue(enclaveFuture.isDone)
        val shoutingEnclave = enclaveFuture.get(1, SECONDS)
        val shoutingConnection = shoutingEnclave.setDownstream(createDynamicEnclaveletHostHandler())

        val recorder = StringRecordingHandler()
        val shouter = connect(shoutingConnection, recorder)

        shouter.send(MESSAGE)
        assertEquals(1, clientSession.awaitResponseFor(seconds = 10))
        assertEquals(1, recorder.calls.size)
        assertEquals(MESSAGE.toUpperCase(), recorder.calls[0])

        log.info("Done!")
    }

    @Test
    fun testWithBrokenEnclave() {
        exception.expect(RuntimeException::class.java)
        exception.expectMessage(BrokenEnclavelet.BrokenException::class.java.name)
        exception.expectMessage("CANNOT START!")

        val multiplexChannel = clientSession.connection

        val brokenJar = testEnclaves.getEnclaveJar(BrokenEnclavelet::class.java)
        val enclaveFuture = multiplexChannel.loader.sendJar(brokenJar.toByteBuffer())

        assertEquals(1, clientSession.awaitResponseFor(enclaveFuture, seconds = 10))
        assertFalse(enclaveFuture.isCompletedExceptionally)
        assertTrue(enclaveFuture.isDone)
        val brokenEnclave = enclaveFuture.get(1, SECONDS)

        val brokenHostConnection = brokenEnclave.setDownstream(createDynamicEnclaveletHostHandler())
        val brokenFuture = brokenHostConnection.channels.addDownstream(object : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                fail("Should not reach here")
            }
        })

        clientSession.awaitResponseFor(brokenFuture, seconds = 10)
    }

    @Test
    fun testWithGarbageEnclave() {
        exception.expect(StatusRuntimeException::class.java)
        exception.expectMessage("UNKNOWN")

        val multiplexChannel = clientSession.connection
        val garbage = ByteBuffer.wrap(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        val enclaveFuture = multiplexChannel.loader.sendJar(garbage)
        try {
            clientSession.awaitResponseFor(enclaveFuture, seconds = 10)
        } finally {
            assertTrue(enclaveFuture.isCompletedExceptionally)
        }
    }

    @Test
    fun testMultipleSessionsWithSameRemoteEnclave() {
        val multiplexChannel = clientSession.connection

        val lisperJar = testEnclaves.getEnclaveJar(LispingEnclavelet::class.java)
        val enclaveFuture = multiplexChannel.loader.sendJar(lisperJar.toByteBuffer())

        assertEquals(1, clientSession.awaitResponseFor(enclaveFuture, seconds = 10))
        assertFalse(enclaveFuture.isCompletedExceptionally)
        assertTrue(enclaveFuture.isDone)
        val lispingEnclave = enclaveFuture.get(1, SECONDS)
        val lispingConnection = lispingEnclave.setDownstream(createDynamicEnclaveletHostHandler())

        val recorder1 = StringRecordingHandler()
        val lisper1 = connect(lispingConnection, recorder1)
        lisper1.send(OTHER_MESSAGE)
        assertEquals(1, clientSession.awaitResponseFor(seconds = 10))
        assertEquals(1, recorder1.calls.size)
        assertEquals("The Theik'th thixth theep ith thick.", recorder1.calls[0])

        val recorder2 = StringRecordingHandler()
        val lisper2 = connect(lispingConnection, recorder2)
        lisper2.send(MESSAGE)
        assertEquals(1, clientSession.awaitResponseFor(seconds = 10))
        assertEquals(1, recorder2.calls.size)
        assertEquals("The thellth thea thellth on the thea thore.", recorder2.calls[0])
    }

    private fun <CONNECTION> connect(enclave: EnclaveletHostHandler.Connection, handler: Handler<CONNECTION>): CONNECTION {
        val future = enclave.channels.addDownstream(handler)
        assertEquals(1, clientSession.awaitResponseFor(future, seconds = 10))
        assertFalse(future.isCompletedExceptionally)
        assertTrue(future.isDone)
        return future.get(1, SECONDS).connection
    }

    class EnclaveletClient(private val enclaveletHost: EnclaveletHostStub) {
        fun <CONNECTION> addDownstream(downstream: Handler<CONNECTION>, queueSize: Int): Session<CONNECTION> {
            return Session(enclaveletHost, downstream, queueSize)
        }

        private class GrpcSender(private val requestObserver: StreamObserver<ClientMessage>) : LeafSender() {
            override fun sendSerialized(serializedBuffer: ByteBuffer) {
                requestObserver.onNext(ClientMessage.newBuilder()
                    .setBlob(ByteString.copyFrom(serializedBuffer))
                    .build()
                )
            }
        }

        class Session<CONNECTION>(
            enclaveletHost: EnclaveletHostStub,
            downstream: Handler<CONNECTION>,
            queueSize: Int
        ) {
            private val serverMessages = ArrayBlockingQueue<StreamMessage<ServerMessage>>(queueSize)
            private val requestObserver = enclaveletHost.openSession(QueuingStreamObserver(serverMessages))
            private val connected: HandlerConnected<CONNECTION> = HandlerConnected(downstream, downstream.connect(GrpcSender(requestObserver)))

            val connection: CONNECTION = connected.connection
            var isClosed: Boolean = false
                private set

            private fun receiveMessages(onError: Consumer<Throwable>): Int {
                var count = 0
                poll@ while (true) {
                    when (val message = serverMessages.poll() ?: break@poll) {
                        is StreamMessage.Next -> {
                            connected.onReceive(message.next.blob.asReadOnlyByteBuffer())
                            ++count
                        }
                        is StreamMessage.Error -> message.error.run {
                            onError.accept(this)
                            throw this
                        }
                        is StreamMessage.Completed -> {
                            isClosed = true
                            break@poll
                        }
                        else -> throw IllegalStateException("Unknown ServerMessage $message")
                    }
                }
                return count
            }

            @Throws(InterruptedException::class)
            fun awaitResponseFor(future: CompletableFuture<*>, seconds: Long): Int {
                val startTime = System.nanoTime()
                val endTime = startTime + Duration.ofSeconds(seconds).toNanos()
                var totalCount = 0
                while (!future.isDone && !isClosed && System.nanoTime() < endTime)  {
                    totalCount += receiveMessages(Consumer { throwable -> future.completeExceptionally(throwable) })
                    if (future.isDone) {
                        break
                    }
                    Thread.sleep(500)
                }
                return if (totalCount == 0 && isClosed) -1 else totalCount
            }

            @Throws(InterruptedException::class)
            fun awaitResponseFor(seconds: Long): Int {
                val startTime = System.nanoTime()
                val endTime = startTime + Duration.ofSeconds(seconds).toNanos()
                while (!isClosed && System.nanoTime() < endTime) {
                    val count = receiveMessages(Consumer { })
                    if (count != 0) {
                        return count
                    }
                    Thread.sleep(500)
                }
                return if (isClosed) -1 else 0
            }
        }
    }
}
