package com.r3.conclave.plugin.enclave.gradle.test

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave

class TestEnclave : Enclave(), EnclaveCall {
    override fun invoke(input: ByteArray) : ByteArray {
        TODO("Should not be invoked")
    }
}