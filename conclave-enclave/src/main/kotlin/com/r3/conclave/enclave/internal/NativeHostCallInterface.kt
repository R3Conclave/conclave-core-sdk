package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.CallInitiator.Companion.EMPTY_BYTE_BUFFER
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.NativeMessageType
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer
import java.util.*

class NativeHostCallInterface : HostCallInterface() {
    private inner class StackFrame(
            val callType: HostCallType,
            var exceptionBuffer: ByteBuffer?,
            var returnBuffer: ByteBuffer?)

    private val threadStacks = ThreadLocal<Stack<StackFrame>>()
    private val stack: Stack<StackFrame>
        get() {
        if (threadStacks.get() == null) {
            threadStacks.set(Stack<StackFrame>())
        }
        return threadStacks.get()
    }

    override fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        stack.push(StackFrame(callType, null, null))

        val paramBytes = ByteArray(parameterBuffer.remaining())
        parameterBuffer.get(paramBytes)
        Native.jvmOcallCon1025(callType.toShort(), NativeMessageType.CALL.toByte(), paramBytes)

        val stackFrame = stack.pop()

        stackFrame.exceptionBuffer?.let {}

        return if (callType.hasReturnValue) {
            checkNotNull(stackFrame.returnBuffer)
        } else {
            EMPTY_BYTE_BUFFER
        }
    }

    override fun acceptCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        val callHandler = getCallHandler(callType)
        return callHandler.handleCall(parameterBuffer)
    }

    /**
     * Handle Ecalls that originate from the host.
     */
    fun handleEcall(callTypeID: Short, ecallType: NativeMessageType, data: ByteBuffer) {
        when (ecallType) {
            NativeMessageType.CALL -> handleCallEcall(EnclaveCallType.fromShort(callTypeID), data)
            NativeMessageType.RETURN -> handleReturnEcall(HostCallType.fromShort(callTypeID), data)
            NativeMessageType.EXCEPTION -> handleExceptionEcall(HostCallType.fromShort(callTypeID), data)
        }
    }

    private fun handleCallEcall(callType: EnclaveCallType, parameterBuffer: ByteBuffer) {
        val returnBuffer = acceptCall(callType, parameterBuffer)
        if (callType.hasReturnValue) {
            Native.jvmOcallCon1025(callType.toShort(), NativeMessageType.RETURN.toByte(), returnBuffer.getRemainingBytes())
        }
    }

    private fun handleReturnEcall(callType: HostCallType, returnBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Return Ecall type mismatch." }
        stack.peek().returnBuffer = ByteBuffer.wrap(returnBuffer.getRemainingBytes())
    }

    private fun handleExceptionEcall(callType: HostCallType, exceptionBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Exception Ecall type mismatch." }
        stack.peek().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getRemainingBytes())
    }
}
