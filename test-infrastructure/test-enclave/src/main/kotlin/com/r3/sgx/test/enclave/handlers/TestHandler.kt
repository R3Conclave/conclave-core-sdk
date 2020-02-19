package com.r3.sgx.test.enclave.handlers

import com.r3.sgx.test.EnclaveJvmTest
import com.r3.sgx.test.enclave.TestEnclave
import com.r3.sgx.test.enclave.messages.MessageType

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