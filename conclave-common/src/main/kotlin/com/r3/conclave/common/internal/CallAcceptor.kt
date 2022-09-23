package com.r3.conclave.common.internal

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

abstract class CallAcceptor<CALL_ID_TYPE> {
    private val callHandlers = ConcurrentHashMap<CALL_ID_TYPE, CallHandler>()

    fun registerCallHandler(callType: CALL_ID_TYPE, handler: CallHandler) {
        check(callHandlers[callType] == null) { "Call handler already registered for $callType." }
        callHandlers[callType] = handler
    }

    fun acceptCall(callType: CALL_ID_TYPE, parameterBuffer: ByteBuffer): ByteBuffer {
        val callHandler = checkNotNull(callHandlers[callType]) { "No call handler has been registered for $callType." }
        return callHandler.handleCall(parameterBuffer)
    }
}
