package com.r3.sgx.proto.host

import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageV3
import com.r3.sgx.core.common.ProtoSender
import com.r3.sgx.core.common.SimpleMuxingHandler
import com.r3.sgx.core.common.SimpleProtoHandler
import com.r3.sgx.core.common.Try
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.RootEnclave
import com.r3.sgx.dynamictesting.EnclaveBuilder
import com.r3.sgx.dynamictesting.TestEnclavesBasedTest
import com.r3.sgx.testing.EchoEnclave
import com.r3.sgx.testing.StringHandler
import com.r3.sgx.testing.StringSender
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.test.assertEquals

class ProtoEnclaveTest : TestEnclavesBasedTest() {

    @Rule
    @JvmField
    val exception = ExpectedException.none()

    val enclaveBuilder = EnclaveBuilder()
            .withClass(Ecall::class.java)
            .withClass(GeneratedMessageV3::class.java)
            .withClass(Try::class.java)

    @Test
    fun exceptionsAreRethrownInEnclave() {
        class ThrowingHandler : StringHandler() {
            override fun onReceive(sender: StringSender, string: String) {
                throw IllegalStateException("Go away!")
            }
        }

        val handler = ThrowingHandler()
        val sender = createEnclave(EchoEnclave::class.java, enclaveBuilder).addDownstream(handler)
        exception.expectMessage("Go away!")
        sender.send("")
    }

    @Test
    fun reentrantStreamFromHostToEnclaveAndBack() {
        class StreamingHostHandler(val inputStream: InputStream, val outputStream: OutputStream, bufferSize: Int) : SimpleProtoHandler<Ocall, Ecall>(Ocall.parser()) {
            val buffer = ByteArray(bufferSize)
            override fun onReceive(connection: ProtoSender<Ecall>, message: Ocall) {
                if (message.isRequest) {
                    // This was an initial request from the enclave to pull from the input
                } else {
                    // This is a push from the enclave to the output
                    outputStream.write(message.bytes.toByteArray())
                }
                // Send next chunk
                val size = inputStream.read(buffer)
                val bytes = if (size > 0) ByteString.copyFrom(buffer, 0, size) else ByteString.EMPTY
                val chunk = Ecall.newBuilder()
                        .setIsRequest(false)
                        .setBytes(bytes)
                        .build()
                connection.send(chunk)
            }
        }

        class StreamingEnclaveHandler : SimpleProtoHandler<Ecall, Ocall>(Ecall.parser()) {
            override fun onReceive(connection: ProtoSender<Ocall>, message: Ecall) {
                if (message.isRequest) {
                    // This is the initial request from the host. Start pulling
                    val ocall = Ocall.newBuilder()
                            .setIsRequest(true)
                            .build()
                    connection.send(ocall)
                } else {
                    if (message.bytes.isEmpty) {
                        // No more input
                    } else {
                        // This is a chunk from the host as a consequence of the pull. Forward to host.
                        val ocall = Ocall.newBuilder()
                                .setIsRequest(false)
                                .setBytes(message.bytes)
                                .build()
                        connection.send(ocall)
                    }
                }
            }
        }

        class StreamingEnclave : RootEnclave() {
            override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
                mux.addDownstream(StreamingEnclaveHandler())
            }
        }

        val bytesToRepeat = byteArrayOf(0xd, 0xe, 0xa, 0xd, 0xb, 0xe, 0xe, 0xf)
        val inputStream = repeat(bytesToRepeat, 10_000_000) // 80 MB
        val outputStream = check(bytesToRepeat)
        val host = StreamingHostHandler(inputStream, outputStream, 9999)
        val sender = createEnclave(StreamingEnclave::class.java, enclaveBuilder).addDownstream(host)
        val initialEcall = Ecall.newBuilder()
                .setIsRequest(true)
                .build()
        sender.send(initialEcall)
    }

    @Test
    fun deepRecursionWorks() {
        class RecursingHost : SimpleProtoHandler<Ocall, Ecall>(Ocall.parser()) {
            var called = 0
            override fun onReceive(connection: ProtoSender<Ecall>, message: Ocall) {
                called++
                val remaining = message.bytes.asReadOnlyByteBuffer().getInt()
                if (remaining == 0) {
                    return
                } else {
                    val payload = ByteArray(4)
                    ByteBuffer.wrap(payload).putInt(remaining.dec())
                    connection.send(Ecall.newBuilder().setBytes(ByteString.copyFrom(payload)).build())
                }
            }
        }

        class RecursingEnclaveHandler : SimpleProtoHandler<Ecall, Ocall>(Ecall.parser()) {
            override fun onReceive(connection: ProtoSender<Ocall>, message: Ecall) {
                val remaining = message.bytes.asReadOnlyByteBuffer().getInt()
                if (remaining == 0) {
                    return
                } else {
                    val payload = ByteArray(4)
                    ByteBuffer.wrap(payload).putInt(remaining.dec())
                    connection.send(Ocall.newBuilder().setBytes(ByteString.copyFrom(payload)).build())
                }
            }
        }

        class RecursingEnclave : RootEnclave() {
            override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
                mux.addDownstream(RecursingEnclaveHandler())
            }
        }

        val host = RecursingHost()
        val sender = createEnclave(RecursingEnclave::class.java, enclaveBuilder).addDownstream(host)
        val payload = ByteArray(4)
        ByteBuffer.wrap(payload).putInt(1000)
        sender.send(Ecall.newBuilder().setBytes(ByteString.copyFrom(payload)).build())
        assertEquals(500, host.called)
    }
}

fun repeat(sample: ByteArray, times: Int): InputStream {
    val total = sample.size * times
    return object : InputStream() {
        private var pos: Long = 0
        override fun read(): Int {
            return if (pos < total) {
                sample[(pos++ % sample.size).toInt()].toInt()
            } else {
                -1
            }
        }
    }
}

fun check(sample: ByteArray): OutputStream {
    return object : OutputStream() {
        private var pos: Long = 0
        override fun write(byte: Int) {
            val expected = sample[(pos++ % sample.size).toInt()].toInt()
            if (expected != byte) {
                throw IllegalStateException("Stream at byte $pos differs! Expected $expected, Got $byte")
            }
        }
    }
}