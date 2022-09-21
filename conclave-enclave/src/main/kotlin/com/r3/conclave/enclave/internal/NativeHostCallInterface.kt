package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.CallInitiator.Companion.EMPTY_BYTE_BUFFER
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer
import java.util.*

class NativeHostCallInterface : HostCallInterface() {
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

    override fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        stack.push(StackFrame(callType, null))

        val paramBytes = ByteArray(parameterBuffer.remaining())
        parameterBuffer.get(paramBytes)
        Native.jvmOcallCon1025(callType.toShort(), false, paramBytes)

        val stackFrame = stack.pop()

        return if (callType.hasReturnValue) {
            checkNotNull(stackFrame.returnBuffer)
        } else {
            EMPTY_BYTE_BUFFER
        }
    }

    /**
     * Handle Ecalls that originate from the host.
     */
    fun handleEcall(callTypeID: Short, isReturn: Boolean, data: ByteBuffer) {
        when (isReturn) {
            true -> handleReturnEcall(HostCallType.fromShort(callTypeID), data)
            false -> handleInitEcall(EnclaveCallType.fromShort(callTypeID), data)
        }
    }

    private fun handleReturnEcall(callType: HostCallType, returnBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Return Ecall type mismatch." }
        stack.peek().returnBuffer = returnBuffer.let { ByteBuffer.wrap(it.getRemainingBytes()) }
    }

    private fun handleInitEcall(callType: EnclaveCallType, parameterBuffer: ByteBuffer) {
        val returnBuffer = acceptCall(callType, parameterBuffer) ?: EMPTY_BYTE_BUFFER
        if (callType.hasReturnValue) {
            Native.jvmOcallCon1025(callType.toShort(), true, returnBuffer.getRemainingBytes())
        }
    }
}
