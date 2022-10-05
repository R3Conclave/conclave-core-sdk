package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer
import java.util.Stack

typealias StackFrame = CallInterfaceStackFrame<EnclaveCallType>

/**
 * This class is the implementation of the [HostEnclaveInterface] for native enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (ecalls and ocalls).
 */
class NativeHostEnclaveInterface(private val enclaveId: Long) : HostEnclaveInterface() {
    /**
     * Each thread has a lazily created stack which contains a frame for the currently active enclave call.
     * When a message arrives from the enclave, this stack is used to associate the return value with the corresponding call.
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
     * This should not be called directly, but instead by implementations in [HostEnclaveInterface].
     */
    override fun initiateOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.push(StackFrame(callType, null, null))

        NativeApi.sendEcall(
                enclaveId, callType.toByte(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer.getAllBytes(avoidCopying = true))

        val stackFrame = stack.pop()

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handler low level messages arriving from the enclave.
     */
    fun handleOcall(enclaveId: Long, callTypeID: Byte, ocallType: CallInterfaceMessageType, data: ByteBuffer) {
        checkEnclaveID(enclaveId)
        when (ocallType) {
            CallInterfaceMessageType.CALL -> handleCallOcall(HostCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.RETURN -> handleReturnOcall(EnclaveCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.EXCEPTION -> handleExceptionOcall(EnclaveCallType.fromByte(callTypeID), data)
        }
    }

    /**
     * Handle call initiations from the enclave.
     * This method propagates the call to the appropriate host side call handler. If a return value is produced or an
     * exception occurs, a reply message is sent back to the enclave.
     */
    private fun handleCallOcall(callType: HostCallType, parameterBuffer: ByteBuffer) {
        try {
            handleIncomingCall(callType, parameterBuffer)?.let {
                /**
                 * If there was a non-null return value, send it back to the enclave.
                 * If no value is received by the enclave, then [com.r3.conclave.enclave.internal.NativeEnclaveHostInterface.initiateOutgoingCall]
                 * will return null to the caller on the enclave side.
                 */
                NativeApi.sendEcall(enclaveId, callType.toByte(), CallInterfaceMessageType.RETURN.toByte(), it.getAllBytes(avoidCopying = true))
            }
        } catch (throwable: Throwable) {
            val serializedException = ThrowableSerialisation.serialise(throwable)
            NativeApi.sendEcall(enclaveId, callType.toByte(), CallInterfaceMessageType.EXCEPTION.toByte(), serializedException)
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
