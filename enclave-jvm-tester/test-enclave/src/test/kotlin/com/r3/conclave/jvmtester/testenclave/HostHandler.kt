package com.r3.conclave.jvmtester.testenclave

import com.r3.sgx.core.common.Handler
import com.r3.sgx.core.common.Sender
import com.r3.conclave.jvmtester.testenclave.messages.MessageType
import com.r3.conclave.jvmtester.api.proto.ByteCodeResult
import com.r3.conclave.jvmtester.api.proto.TestResult
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

class HostHandler(private val outputDir: Path) : Handler<Sender> {
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
            MessageType.BYTECODE_DUMP.ordinal -> writeByteCodeToFile(input.slice())
            MessageType.DJVM_TEST.ordinal -> assertTest(assertedDJVMTests, input.slice())
        }
    }

    private fun writeByteCodeToFile(input: ByteBuffer) {
        val message = ByteCodeResult.parseFrom(input)
        val file = outputDir.resolve("${message.className.replace('/','.')}.class")
        Files.write(file, message.byteCode.toByteArray())
        classesByteCodeReceived.add(message.className)
    }

    companion object {
        fun assertTest(assertedTests: MutableList<String>, input: ByteBuffer) {
            val message = TestResult.parseFrom(input)
            assertResult(message.className, message.result.toByteArray())
            assertedTests.add(message.className)
        }
    }
}