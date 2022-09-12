package com.r3.conclave.enclave.internal;

import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.decodeDynamicLengthField
import java.nio.ByteBuffer


class EnclaveCallManager {
    companion object {
        private val callTypeValues = EnclaveCallType.values()
    }

    fun receiveMessage(messageBytes: ByteBuffer) {
        val messageType = callTypeValues[messageBytes.short.toInt()]
        val messageLength = messageBytes.decodeDynamicLengthField()

        // TODO
    }
}
