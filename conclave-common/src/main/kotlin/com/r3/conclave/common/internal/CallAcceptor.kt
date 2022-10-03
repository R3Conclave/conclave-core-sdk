package com.r3.conclave.common.internal

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * This class maintains a mapping of call handlers to call types.
 */
abstract class CallAcceptor<CALL_ID_TYPE> {
    private val callHandlers = ConcurrentHashMap<CALL_ID_TYPE, CallHandler>()

    /**
     * Add a call handler for a specific call type.
     * If the call type already has a handler, throw an exception.
     */
    fun registerCallHandler(callType: CALL_ID_TYPE, handler: CallHandler) {
        check(!callHandlers.containsKey(callType)) { "Call handler already registered for $callType." }
        callHandlers[callType] = handler
    }

    /**
     * Route a call to a registered call handler.
     * If no handler is registered, throw an exception.
     */
    fun acceptCall(callType: CALL_ID_TYPE, parameterBuffer: ByteBuffer): ByteBuffer? {
        val callHandler = checkNotNull(callHandlers[callType]) { "No call handler has been registered for $callType." }
        return callHandler.handleCall(parameterBuffer)
    }
}
