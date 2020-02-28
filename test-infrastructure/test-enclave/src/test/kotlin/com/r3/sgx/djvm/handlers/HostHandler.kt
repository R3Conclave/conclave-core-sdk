package com.r3.sgx.djvm.handlers

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import com.r3.sgx.test.assertResult
import com.r3.sgx.test.enclave.messages.MessageType
import com.r3.sgx.test.proto.ByteCodeResult
import com.r3.sgx.test.proto.TestResult
import java.io.FileOutputStream
import java.nio.ByteBuffer

class HostHandler : Handler<Sender> {
    val assertedTests = mutableListOf<String>()
    val assertedSandboxedTests = mutableListOf<String>()
    val classesByteCodeReceived = mutableListOf<String>()
    val assertedDJVMTests = mutableListOf<String>()

    override fun connect(upstream: Sender) = upstream

    override fun onReceive(connection: Sender, input: ByteBuffer) {
        val messageType = input.int
        when (messageType) {
            MessageType.TEST.ordinal -> assertTest(assertedTests, input.slice())
            MessageType.SANDBOX_TEST.ordinal -> assertTest(assertedSandboxedTests, input.slice())
            MessageType.BYTECODE_DUMP.ordinal -> writeByteCodeToFile(classesByteCodeReceived, input.slice())
            MessageType.DJVM_TEST.ordinal -> assertTest(assertedDJVMTests, input.slice())
        }
    }

    companion object {
        fun assertTest(assertedTests: MutableList<String>, input: ByteBuffer) {
            val message = TestResult.parseFrom(input)
            assertResult(message.className, message.result.toByteArray())
            assertedTests.add(message.className)
        }

        fun writeByteCodeToFile(classesByteCodeReceived : MutableList<String>, input: ByteBuffer) {
            val message = ByteCodeResult.parseFrom(input)
            val fileName = "${message.className.replace('/','.')}.class"
            FileOutputStream(fileName).use { fos ->
                fos.write(message.byteCode.toByteArray())
            }
            classesByteCodeReceived.add(message.className)
        }
    }
}