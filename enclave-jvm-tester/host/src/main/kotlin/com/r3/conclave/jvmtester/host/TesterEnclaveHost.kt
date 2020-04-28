package com.r3.conclave.jvmtester.host

import com.google.protobuf.ByteString
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.jvmtester.api.EnclaveJvmTest
import com.r3.conclave.jvmtester.api.TestSerializable
import com.r3.conclave.jvmtester.api.enclave.proto.*
import java.nio.file.Files
import java.nio.file.Path

class TesterEnclaveHost : AutoCloseable {
    private val enclaveHost = EnclaveHost.load("com.r3.conclave.jvmtester.enclave.TesterEnclave")

    fun start(spid: OpaqueBytes?, attestationKey: String?) {
        enclaveHost.start(spid, attestationKey)
    }

    fun loadJar(jarFile: Path) {
        callEnclave {
            sendJarBuilder.data = ByteString.copyFrom(Files.readAllBytes(jarFile))
        }
    }

    fun generateBytecode(className: String): BytecodeResult {
        val response = callEnclave {
            bytecodeRequestBuilder.className = className
        }
        return BytecodeResult.parseFrom(response)
    }

    fun executeTest(mode: ExecuteTest.Mode, test: EnclaveJvmTest): ByteArray {
        val response = callEnclave {
            executeTestBuilder.mode = mode
            executeTestBuilder.className = test.javaClass.name
            if (test is TestSerializable) {
                executeTestBuilder.input = ByteString.copyFrom(test.getTestInput())
            }
        }
        return TestResult.parseFrom(response).result.toByteArray()
    }

    private inline fun callEnclave(block: Request.Builder.() -> Unit): ByteArray? {
        val request = Request.newBuilder()
        block(request)
        return enclaveHost.callEnclave(request.build().toByteArray())
    }

    override fun close() {
        callEnclave { clearJars = ClearJars.getDefaultInstance() }
        // destroy can trigger an assertion failure in Avian
//        enclaveHost.close()
    }
}
