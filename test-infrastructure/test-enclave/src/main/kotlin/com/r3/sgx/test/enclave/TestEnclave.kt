package com.r3.sgx.test.enclave

import com.google.protobuf.ByteString
import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import com.r3.sgx.djvm.DJVMBase
import com.r3.sgx.test.EnclaveJvmTest
import com.r3.sgx.test.enclave.djvm.SandboxRunner
import com.r3.sgx.test.enclave.djvm.url.DJVMMemoryURLStreamHandler
import com.r3.sgx.test.enclave.handlers.SandboxTestHandler
import com.r3.sgx.test.enclave.handlers.TestHandler
import com.r3.sgx.test.enclave.handlers.TestRunner
import com.r3.sgx.test.enclave.messages.MessageType
import com.r3.sgx.test.proto.*
import com.r3.sgx.test.serialization.TestSerializable
import com.r3.sgx.utils.classloaders.MemoryClassLoader
import com.r3.sgx.utils.classloaders.MemoryURL
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.function.Consumer

class TestEnclave : Enclavelet() {
    override fun createReportData(api: EnclaveApi): ByteCursor<SgxReportData> {
        val report = Cursor.allocate(SgxReportData)
        val buffer = report.getBuffer()
        buffer.put(ByteArray(buffer.capacity()) { 0 })
        return report
    }

    override fun createHandler(api: EnclaveApi): Handler<*> {
        return EnclaveHandler()
    }

    class EnclaveHandler : Handler<EnclaveConnection> {
        private val testHandler = object : TestHandler {
            override fun messageType(): MessageType {
                return MessageType.TEST
            }
        }
        private val sandboxTestHandler = object : TestRunner, SandboxTestHandler() {
            override fun runTest(test: EnclaveJvmTest, input: Any?) : Any? {
                return SandboxRunner().run(test.javaClass.name, input)
            }
        }
        private val byteCodeHandler = object : SandboxTestHandler() {
            override fun messageType(): MessageType {
                return MessageType.BYTECODE_DUMP
            }
        }
        private val djvmTestHandler = object : TestRunner, SandboxTestHandler() {
            override fun messageType(): MessageType {
                return MessageType.DJVM_TEST
            }

        }

        override fun connect(upstream: Sender) = EnclaveConnection(upstream)

        override fun onReceive(connection: EnclaveConnection, input: ByteBuffer) {
            val messageType = input.int
            when (messageType) {
                MessageType.JAR.ordinal -> processJar(connection, input.slice())
                MessageType.CLEAR_JARS.ordinal -> DJVMMemoryURLStreamHandler.clear()
                MessageType.TEST.ordinal -> runTest(connection, input.slice(), testHandler)
                MessageType.SANDBOX_TEST.ordinal -> runTest(connection, input.slice(), sandboxTestHandler)
                MessageType.BYTECODE_DUMP.ordinal -> generateByteCode(connection, input.slice(), byteCodeHandler)
                MessageType.DJVM_TEST.ordinal -> runTest(connection, input.slice(), djvmTestHandler)
            }
        }

        companion object {
            private fun processJar(connection: TestEnclave.EnclaveConnection, input: ByteBuffer) {
                val message = SendJar.parseFrom(input)
                val jarByteBuffer = message.data.asReadOnlyByteBuffer()
                val memoryURL = DJVMMemoryURLStreamHandler.createURL(jarByteBuffer.sha256.hashKey, jarByteBuffer)
                connection.userJars.add(memoryURL)
            }

            private fun runTest(connection: EnclaveConnection, input: ByteBuffer, testHandler: TestHandler) {
                testHandler.setup(connection)
                val message = ExecuteTest.parseFrom(input)
                val memoryClassLoader = MemoryClassLoader(connection.userJars)
                val clazz = memoryClassLoader.loadClass(message.className)
                val test = clazz.newInstance() as EnclaveJvmTest

                val testResultBuilder = TestResult.newBuilder()
                testResultBuilder.className = message.className

                val testInput = if (message.hasInput()) {
                    (test as TestSerializable).deserializeTestInput(message.input.toByteArray())
                } else {
                    null
                }

                try {
                    val result = testHandler.runTest(test, testInput)
                    val serializedOutput = test.serializeTestOutput(result)
                    testResultBuilder.result = ByteString.copyFrom(serializedOutput)
                } finally {
                    testHandler.destroy()
                    val testResultBytes = testResultBuilder.build().toByteArray()
                    connection.send(Int.SIZE_BYTES + testResultBytes.size, Consumer { buffer ->
                        buffer.putInt(testHandler.messageType().ordinal)
                        buffer.put(testResultBytes)
                    })
                }
            }

            /**
             * Generate deterministic bytecode for the requested class, send to the host to be stored in a .class file
             */
            private fun generateByteCode(connection: TestEnclave.EnclaveConnection, input: ByteBuffer, testHandler: TestHandler) {
                testHandler.setup(connection)
                val message = ByteCodeRequest.parseFrom(input)

                try {
                    val loadedClass = DJVMBase.sandboxClassLoader.loadForSandbox(message.className)
                    val byteCodeResultBytes = ByteCodeResult.newBuilder()
                            .setClassName(loadedClass.name)
                            .setByteCode(ByteString.copyFrom(loadedClass.byteCode.bytes))
                            .build()
                            .toByteArray()
                    connection.send(Int.SIZE_BYTES + byteCodeResultBytes.size, Consumer { buffer ->
                        buffer.putInt(testHandler.messageType().ordinal)
                        buffer.put(byteCodeResultBytes)
                    })
                } finally {
                    testHandler.destroy()
                }
            }
        }
    }

    class EnclaveConnection(private val upstream: Sender) : Sender {
        val userJars = mutableListOf<MemoryURL>()

        override fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>) {
            upstream.send(needBytes, serializers)
        }
    }
}

const val SHA256_BYTES = 32

val ByteBuffer.sha256: ByteArray get() {
    return MessageDigest.getInstance("SHA-256").let { hash ->
        hash.update(slice())
        hash.digest()
    }
}

val ByteArray.hashKey: String get() {
    // Our 256 bit hash consists of 64 nybbles.
    return BigInteger(1, this)
            .toString(16).toLowerCase().padStart(2 * SHA256_BYTES, '0')
}
