package com.r3.conclave.sample.enclave

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.enclave.Enclave

class ReverseEnclave : EnclaveCall, Enclave() {
    override fun invoke(bytes: ByteArray): ByteArray? = bytes.reversedArray()
}
