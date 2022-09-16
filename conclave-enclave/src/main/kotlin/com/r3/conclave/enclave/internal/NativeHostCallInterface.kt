package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer
import java.util.*

class NativeHostCallInterface : HostCallInterface() {
    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
        val EMPTY_BYTE_BUFFER: ByteBuffer = ByteBuffer.wrap(EMPTY_BYTE_ARRAY).asReadOnlyBuffer()
    }

    private inner class StackFrame(
            val callType: HostCallType,
            var returnBuffer: ByteBuffer?)

    private val threadStacks = ThreadLocal<Stack<StackFrame>>()
    private val stack: Stack<StackFrame>
        get() {
        if (threadStacks.get() == null) {
            threadStacks.set(Stack<StackFrame>())
        }
        return threadStacks.get()
    }

    override fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer?): ByteBuffer? {
        val parameterBufferActual = if (callType.hasParameters) {
            requireNotNull(parameterBuffer) { "Missing parameter buffer for host call of type ${callType.name}." }
        } else {
            EMPTY_BYTE_BUFFER
        }

        stack.push(StackFrame(callType, null))

        val paramBytes = ByteArray(parameterBufferActual.remaining())
        parameterBufferActual.get(paramBytes)
        Native.jvmOcallCon1025(callType.toShort(), false, paramBytes)

        val stackFrame = stack.pop()

        if (callType.hasReturnValue) {
            checkNotNull(stackFrame.returnBuffer)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handle Ecalls that originate from the host.
     */
    fun handleEcall(callTypeID: Short, isReturn: Boolean, data: ByteBuffer?) {
        when (isReturn) {
            true -> handleReturnEcall(HostCallType.fromShort(callTypeID), data)
            false -> handleInitEcall(EnclaveCallType.fromShort(callTypeID), data)
        }
    }

    private fun handleReturnEcall(callType: HostCallType, returnBuffer: ByteBuffer?) {
        check(callType == stack.peek().callType) { "Return Ecall type mismatch." }
        stack.peek().returnBuffer = returnBuffer?.let { ByteBuffer.wrap(it.getRemainingBytes()) }
    }

    private fun handleInitEcall(callType: EnclaveCallType, parameterBuffer: ByteBuffer?) {
        val returnBytes = acceptCall(callType, parameterBuffer)?.getRemainingBytes()
        if (callType.hasReturnValue) {
            checkNotNull(returnBytes) { "Missing return buffer for enclave call of type ${callType.name}." }
            Native.jvmOcallCon1025(callType.toShort(), true, returnBytes)
        }
    }
}
