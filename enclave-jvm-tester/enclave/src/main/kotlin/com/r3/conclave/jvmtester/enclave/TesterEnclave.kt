package com.r3.conclave.jvmtester.enclave

import com.google.protobuf.ByteString
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.jvmtester.api.EnclaveJvmTest
import com.r3.conclave.jvmtester.api.TestSerializable
import com.r3.conclave.jvmtester.api.enclave.proto.*
import com.r3.conclave.jvmtester.djvm.testutils.DJVMBase
import com.r3.conclave.jvmtester.djvm.testutils.SandboxRunner
import com.r3.conclave.jvmtester.enclave.sandbox.AbstractSandboxEnvironment
import com.r3.conclave.jvmtester.enclave.sandbox.DJVMMemoryURLStreamHandler
import com.r3.sgx.utils.classloaders.MemoryClassLoader
import com.r3.sgx.utils.classloaders.MemoryURL
import java.nio.ByteBuffer

class TesterEnclave : EnclaveCall, Enclave() {
    private val userJars = ArrayList<MemoryURL>()

    private val standardEnv = object : TestEnvironment {
        override fun setup(userJars: List<MemoryURL>) = Unit
        override fun destroy() = Unit
        override fun runTest(test: EnclaveJvmTest, input: Any?): Any? = test.apply(input)
    }
    private val sandboxEnv = object : AbstractSandboxEnvironment() {
        override fun runTest(test: EnclaveJvmTest, input: Any?): Any? = SandboxRunner().run(test.javaClass.name, input)
    }
    private val sandboxOnlyEnv = object : AbstractSandboxEnvironment() {
        override fun runTest(test: EnclaveJvmTest, input: Any?): Any? = test.apply(input)
    }

    override fun invoke(bytes: ByteArray): ByteArray? {
        val request = Request.parseFrom(bytes)
        val response = when (request.requestsCase!!) {
            Request.RequestsCase.SEND_JAR -> {
                processJar(request.sendJar)
                null
            }
            Request.RequestsCase.CLEAR_JARS -> {
                DJVMMemoryURLStreamHandler.clear()
                null
            }
            Request.RequestsCase.EXECUTE_TEST -> runTest(request.executeTest)
            Request.RequestsCase.BYTECODE_REQUEST -> generateBytecode(request.bytecodeRequest)
            Request.RequestsCase.REQUESTS_NOT_SET -> throw IllegalArgumentException("requests not set")
        }
        return response?.build()?.toByteArray()
    }

    private fun processJar(request: SendJar) {
        val jarBytes = request.data.toByteArray()
        val memoryURL = DJVMMemoryURLStreamHandler.createURL(SHA256Hash.hash(jarBytes).toString(), ByteBuffer.wrap(jarBytes))
        userJars += memoryURL
    }

    private fun runTest(request: ExecuteTest): TestResult.Builder {
        val memoryClassLoader = MemoryClassLoader(userJars)
        val clazz = memoryClassLoader.loadClass(request.className)
        val jvmTest = clazz.newInstance() as EnclaveJvmTest

        val testInput = if (request.hasInput()) {
            (jvmTest as TestSerializable).deserializeTestInput(request.input.toByteArray())
        } else {
            null
        }

        val testHandler = when (request.mode!!) {
            ExecuteTest.Mode.STANDARD -> standardEnv
            ExecuteTest.Mode.SANDBOX -> sandboxEnv
            ExecuteTest.Mode.JUST_SETUP_SANDBOX -> sandboxOnlyEnv
        }

        testHandler.setup(userJars)
        val result = try {
            testHandler.runTest(jvmTest, testInput)
        } finally {
            testHandler.destroy()
        }

        return TestResult.newBuilder().setResult(ByteString.copyFrom(jvmTest.serializeTestOutput(result)))
    }

    /**
     * Generate deterministic bytecode for the requested class, send to the host to be stored in a .class file
     */
    private fun generateBytecode(request: BytecodeRequest): BytecodeResult.Builder {
        sandboxEnv.setup(userJars)
        val loadedClass = try {
            DJVMBase.sandboxClassLoader.loadForSandbox(request.className)
        } finally {
            sandboxEnv.destroy()
        }
        return BytecodeResult.newBuilder()
                .setClassName(loadedClass.name)
                .setBytecode(ByteString.copyFrom(loadedClass.byteCode.bytes))
    }
}
