package com.r3.conclave.common.internal

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * This class serves as the basis for enclave and host call interfaces.
 * It both services incoming calls and handles outgoing calls.
 * It maintains an internal mapping of incoming call types to registered call handlers.
 */
abstract class CallInterface<OUTGOING_CALL_TYPE, INCOMING_CALL_TYPE> {
    companion object {
        private val EMPTY_BYTE_BUFFER: ByteBuffer = ByteBuffer.wrap(ByteArray(0)).asReadOnlyBuffer()
    }

    private val callHandlers = ConcurrentHashMap<INCOMING_CALL_TYPE, CallHandler>()

    /**
     * Execute a call and maybe get a return buffer.
     */
    abstract fun executeOutgoingCall(callType: OUTGOING_CALL_TYPE, parameterBuffer: ByteBuffer = EMPTY_BYTE_BUFFER): ByteBuffer?

    /**
     * Execute a call and get a return buffer. Throw an exception if no buffer is returned.
     */
    fun executeOutgoingCallWithReturn(callType: OUTGOING_CALL_TYPE, parameterBuffer: ByteBuffer = EMPTY_BYTE_BUFFER): ByteBuffer {
        return checkNotNull(executeOutgoingCall(callType, parameterBuffer)) {
            "Missing return value from $callType call."
        }
    }

    /**
     * Add a call handler for a specific call type.
     * If the call type already has a handler, throw an exception.
     */
    fun registerCallHandler(callType: INCOMING_CALL_TYPE, handler: CallHandler) {
        check(callHandlers.putIfAbsent(callType, handler) == null) {
            "Call handler already registered for $callType."
        }
    }

    /**
     * Route a call to a registered call handler.
     * If no handler is registered, throw an exception.
     */
    fun handleIncomingCall(callType: INCOMING_CALL_TYPE, parameterBuffer: ByteBuffer): ByteBuffer? {
        val callHandler = checkNotNull(callHandlers[callType]) { "No call handler has been registered for $callType." }
        return callHandler.handleCall(parameterBuffer)
    }
}
