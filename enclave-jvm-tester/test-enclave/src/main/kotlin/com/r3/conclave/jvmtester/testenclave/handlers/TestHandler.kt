package com.r3.conclave.jvmtester.testenclave.handlers

import com.r3.conclave.jvmtester.api.EnclaveJvmTest
import com.r3.conclave.jvmtester.testenclave.TestEnclave
import com.r3.conclave.jvmtester.testenclave.messages.MessageType

interface TestHandler : TestSetup, TestRunner

interface TestSetup {
    fun setup(connection: TestEnclave.EnclaveConnection) {
    }

    fun destroy() {
    }

    fun messageType() : MessageType
}

interface TestRunner {
    fun runTest(test: EnclaveJvmTest, input: Any?) : Any? {
        return test.apply(input)
    }
}