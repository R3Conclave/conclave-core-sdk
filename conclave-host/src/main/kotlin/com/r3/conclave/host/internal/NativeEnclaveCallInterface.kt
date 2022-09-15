package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import java.util.Stack

class NativeEnclaveCallInterface(val enclaveId: Long) : EnclaveCallInterface() {
    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
        val EMPTY_BYTE_BUFFER: ByteBuffer = ByteBuffer.wrap(EMPTY_BYTE_ARRAY).asReadOnlyBuffer()
    }

    private inner class StackFrame(
            val callType: EnclaveCallType,
            var returnBuffer: ByteBuffer?)

    private val threadStacks = ThreadLocal<Stack<StackFrame>>()
    private val stack: Stack<StackFrame> get() {
        if (threadStacks.get() == null) {
            threadStacks.set(Stack<StackFrame>())
        }
        return threadStacks.get()
    }

    override fun initiateCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer?): ByteBuffer? {
        val parameterBufferActual = if (callType.hasParameters) {
            requireNotNull(parameterBuffer) { "No parameters provided to function ${callType.name}" }
        } else {
            EMPTY_BYTE_BUFFER
        }

        stack.push(StackFrame(callType, null))

        val paramBytes = ByteArray(parameterBufferActual.remaining())
        parameterBufferActual.get(paramBytes)
        NativeApi.hostToEnclaveCon1025(enclaveId, callType.toShort(), false, paramBytes)

        val stackFrame = stack.pop()

        if (callType.hasReturnValue) {
            checkNotNull(stackFrame.returnBuffer)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handle ocalls that originate from the enclave.
     */
    fun handleOcall(enclaveId: Long, callTypeID: Short, isReturn: Boolean, data: ByteBuffer?) {
        check(enclaveId == this.enclaveId) { "Enclave ID mismatch." }
        when (isReturn) {
            true -> handleReturnOcall(EnclaveCallType.fromShort(callTypeID), data)
            false -> handleInitOcall(HostCallType.fromShort(callTypeID), data)
        }
    }

    private fun handleReturnOcall(callType: EnclaveCallType, returnBuffer: ByteBuffer?) {
        check(callType == stack.peek().callType) { "Return Ocall type mismatch." }
        stack.peek().returnBuffer = returnBuffer?.let { ByteBuffer.wrap(it.getRemainingBytes()) }
    }

    private fun handleInitOcall(callType: HostCallType, parameterBuffer: ByteBuffer?) {
        val returnBytes = acceptCall(callType, parameterBuffer)?.getRemainingBytes() ?: EMPTY_BYTE_ARRAY
        NativeApi.hostToEnclaveCon1025(enclaveId, callType.toShort(), true, returnBytes)
    }
}
