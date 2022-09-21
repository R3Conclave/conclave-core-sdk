package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CallInitiator.Companion.EMPTY_BYTE_BUFFER
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer
import java.util.Stack

class NativeEnclaveCallInterface(private val enclaveId: Long) : EnclaveCallInterface() {
    private inner class StackFrame(
            val callType: EnclaveCallType,
            var returnBuffer: ByteBuffer?)

    private val threadLocalStacks = ThreadLocal<Stack<StackFrame>>()
    private val stack: Stack<StackFrame> get() {
        if (threadLocalStacks.get() == null) {
            threadLocalStacks.set(Stack<StackFrame>())
        }
        return threadLocalStacks.get()
    }

    override fun initiateCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        stack.push(StackFrame(callType, null))

        val paramBytes = ByteArray(parameterBuffer.remaining())
        parameterBuffer.get(paramBytes)
        NativeApi.hostToEnclaveCon1025(enclaveId, callType.toShort(), false, paramBytes)

        val stackFrame = stack.pop()

        return if (callType.hasReturnValue) {
            checkNotNull(stackFrame.returnBuffer)
        } else {
            EMPTY_BYTE_BUFFER
        }
    }

    /**
     * Handle ocalls that originate from the enclave.
     */
    fun handleOcall(enclaveId: Long, callTypeID: Short, isReturn: Boolean, data: ByteBuffer) {
        check(enclaveId == this.enclaveId) { "Enclave ID mismatch." }
        when (isReturn) {
            true -> handleReturnOcall(EnclaveCallType.fromShort(callTypeID), data)
            false -> handleInitOcall(HostCallType.fromShort(callTypeID), data)
        }
    }

    private fun handleReturnOcall(callType: EnclaveCallType, returnBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Return Ocall type mismatch." }
        stack.peek().returnBuffer = ByteBuffer.wrap(returnBuffer.getRemainingBytes())
    }

    private fun handleInitOcall(callType: HostCallType, parameterBuffer: ByteBuffer) {
        val returnBuffer = acceptCall(callType, parameterBuffer) ?: EMPTY_BYTE_BUFFER
        if (callType.hasReturnValue) {
            NativeApi.hostToEnclaveCon1025(enclaveId, callType.toShort(), true, returnBuffer.getRemainingBytes())
        }
    }
}
