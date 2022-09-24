package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CallInitiator.Companion.EMPTY_BYTE_BUFFER
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.NativeMessageType
import com.r3.conclave.common.internal.ThrowableSerialisation
import com.r3.conclave.utilities.internal.getRemainingBytes
import java.nio.ByteBuffer
import java.util.Stack

class NativeEnclaveCallInterface(private val enclaveId: Long) : EnclaveCallInterface() {
    private inner class StackFrame(
            val callType: EnclaveCallType,
            var exceptionBuffer: ByteBuffer?,
            var returnBuffer: ByteBuffer?)

    private val threadLocalStacks = ThreadLocal<Stack<StackFrame>>()
    private val stack: Stack<StackFrame> get() {
        if (threadLocalStacks.get() == null) {
            threadLocalStacks.set(Stack<StackFrame>())
        }
        return threadLocalStacks.get()
    }

    override fun initiateCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer {
        stack.push(StackFrame(callType, null, null))

        val paramBytes = ByteArray(parameterBuffer.remaining())
        parameterBuffer.get(paramBytes)
        NativeApi.hostToEnclaveCon1025(enclaveId, callType.toShort(), NativeMessageType.CALL, paramBytes)

        val stackFrame = stack.pop()

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return if (callType.hasReturnValue) {
            checkNotNull(stackFrame.returnBuffer)
        } else {
            EMPTY_BYTE_BUFFER
        }
    }

    /**
     * Handle ocalls that originate from the enclave.
     */
    fun handleOcall(enclaveId: Long, callTypeID: Short, ocallType: NativeMessageType, data: ByteBuffer) {
        check(enclaveId == this.enclaveId) { "Enclave ID mismatch." }
        when (ocallType) {
            NativeMessageType.CALL -> handleCallOcall(HostCallType.fromShort(callTypeID), data)
            NativeMessageType.RETURN -> handleReturnOcall(EnclaveCallType.fromShort(callTypeID), data)
            NativeMessageType.EXCEPTION -> handleExceptionOcall(EnclaveCallType.fromShort(callTypeID), data)
        }
    }

    private fun handleCallOcall(callType: HostCallType, parameterBuffer: ByteBuffer) {
        try {
            val returnBuffer = acceptCall(callType, parameterBuffer)
            if (callType.hasReturnValue) {
                NativeApi.hostToEnclaveCon1025(enclaveId, callType.toShort(), NativeMessageType.RETURN, returnBuffer.getRemainingBytes())
            }
        } catch (throwable: Throwable) {
            val serializedException = ThrowableSerialisation.serialise(throwable)
            NativeApi.hostToEnclaveCon1025(enclaveId, callType.toShort(), NativeMessageType.EXCEPTION, serializedException)
        }
    }

    private fun handleReturnOcall(callType: EnclaveCallType, returnBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Return Ocall type mismatch." }
        stack.peek().returnBuffer = ByteBuffer.wrap(returnBuffer.getRemainingBytes())
    }

    private fun handleExceptionOcall(callType: EnclaveCallType, exceptionBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Exception Ocall type mismatch." }
        stack.peek().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getRemainingBytes())
    }
}
