package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CallHandler
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.decodeDynamicLengthField
import java.nio.ByteBuffer


class HostCallManager {
    companion object {
        private val callTypeValues = HostCallType.values()
    }

    val callHandlers = HashMap<HostCallType, CallHandler>()

    fun receiveMessage(messageBytes: ByteBuffer) {
        val messageType = callTypeValues[messageBytes.short.toInt()]
        val messageLength = messageBytes.decodeDynamicLengthField()

        // TODO
    }
}
