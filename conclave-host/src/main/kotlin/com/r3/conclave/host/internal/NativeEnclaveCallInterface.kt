package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.NativeMessageType
import com.r3.conclave.common.internal.ThrowableSerialisation
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer
import java.util.Stack

/**
 * This class is the implementation of the [EnclaveCallInterface] for native enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInitiator]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallAcceptor]
 *  - Handle the low-level details of the messaging protocol (ecalls and ocalls).
 */
class NativeEnclaveCallInterface(private val enclaveId: Long) : EnclaveCallInterface() {
    private inner class StackFrame(
            val callType: EnclaveCallType,
            var exceptionBuffer: ByteBuffer?,
            var returnBuffer: ByteBuffer?)

    /**
     * Each thread has a lazily created stack which contains a frame for the currently active enclave call.
     * When a message arrives from the enclave, this stack is used to associate the return value with the call.
     */
    private val threadLocalStacks = ThreadLocal<Stack<StackFrame>>()
    private val stack: Stack<StackFrame>
        get() {
            if (threadLocalStacks.get() == null) {
                threadLocalStacks.set(Stack<StackFrame>())
            }
            return threadLocalStacks.get()
        }

    private fun checkEnclaveID(id: Long) = check(id == this.enclaveId) { "Enclave ID mismatch" }
    private fun checkCallType(type: EnclaveCallType) = check(type == stack.peek().callType) { "Call type mismatch" }

    /**
     * Internal method for initiating an enclave call with specific arguments.
     * This should not be called directly, but instead by implementations in [EnclaveCallInterface].
     */
    override fun executeCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.push(StackFrame(callType, null, null))

        NativeApi.sendEcall(
                enclaveId, callType.toShort(), NativeMessageType.CALL.toByte(), parameterBuffer.getAllBytes(avoidCopying = true))

        val stackFrame = stack.pop()

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handler low level messages arriving from the enclave.
     */
    fun handleOcall(enclaveId: Long, callTypeID: Short, ocallType: NativeMessageType, data: ByteBuffer) {
        check(enclaveId == this.enclaveId) { "Enclave ID mismatch." }
        when (ocallType) {
            NativeMessageType.CALL -> handleCallOcall(HostCallType.fromShort(callTypeID), data)
            NativeMessageType.RETURN -> handleReturnOcall(EnclaveCallType.fromShort(callTypeID), data)
            NativeMessageType.EXCEPTION -> handleExceptionOcall(EnclaveCallType.fromShort(callTypeID), data)
        }
    }

    /**
     * Handle call initiations from the enclave.
     * This method propagates the call to the appropriate call handler, then serialises and propagates any exceptions
     * which might occur. If a return value is produced, a reply message is sent back to the enclave.
     */
    private fun handleCallOcall(callType: HostCallType, parameterBuffer: ByteBuffer) {
        try {
            acceptCall(callType, parameterBuffer)?.let {
                NativeApi.sendEcall(
                        enclaveId, callType.toShort(), NativeMessageType.RETURN.toByte(), it.getAllBytes(avoidCopying = true))
            }
        } catch (throwable: Throwable) {
            val serializedException = ThrowableSerialisation.serialise(throwable)
            NativeApi.sendEcall(enclaveId, callType.toShort(), NativeMessageType.EXCEPTION.toByte(), serializedException)
        }
    }

    /**
     * Handle return messages originating from the enclave.
     */
    private fun handleReturnOcall(callType: EnclaveCallType, returnBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.peek().returnBuffer = ByteBuffer.wrap(returnBuffer.getAllBytes())
    }

    /**
     * Handle exception messages originating from the enclave.
     */
    private fun handleExceptionOcall(callType: EnclaveCallType, exceptionBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.peek().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getAllBytes())
    }
}
