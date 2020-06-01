package com.r3.conclave.testing

import com.r3.conclave.common.enclave.EnclaveCall

class RecordingEnclaveCall : EnclaveCall {
    val calls = ArrayList<ByteArray>()

    override fun invoke(bytes: ByteArray): ByteArray? {
        calls += bytes
        return null
    }
}
