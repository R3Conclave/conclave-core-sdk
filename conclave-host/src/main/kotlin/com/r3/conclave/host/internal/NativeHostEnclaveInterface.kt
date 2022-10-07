package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer
import kotlin.collections.ArrayDeque

typealias StackFrame = CallInterfaceStackFrame<EnclaveCallType>

/**
 * This class is the implementation of the [HostEnclaveInterface] for native enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (ECalls and OCalls).
 */
class NativeHostEnclaveInterface(private val enclaveId: Long) : HostEnclaveInterface() {
    /**
     * Each thread has a lazily created stack which contains a frame for the currently active enclave call.
     * When a message arrives from the enclave, this stack is used to associate the return value with the corresponding call.
     */
    private val threadLocalStacks = ThreadLocal.withInitial { ArrayDeque<StackFrame>() }
    private val stack get() = threadLocalStacks.get()

    private fun checkEnclaveID(id: Long) = check(id == this.enclaveId) { "Enclave ID mismatch" }
    private fun checkCallType(type: EnclaveCallType) = check(type == stack.last().callType) { "Call type mismatch" }

    /**
     * Internal method for initiating an enclave call with specific arguments.
     * This should not be called directly, but instead by implementations in [HostEnclaveInterface].
     */
    override fun executeOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.addLast(StackFrame(callType, null, null))

        NativeApi.sendECall(
                enclaveId, callType.toByte(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer.getAllBytes(avoidCopying = true))

        val stackFrame = stack.removeLast()

        if (stack.isEmpty()) {
            threadLocalStacks.remove()
        }

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handler low level messages arriving from the enclave.
     */
    fun handleOCall(enclaveId: Long, callTypeID: Byte, ocallType: CallInterfaceMessageType, data: ByteBuffer) {
        checkEnclaveID(enclaveId)
        when (ocallType) {
            CallInterfaceMessageType.CALL -> handleCallOCall(HostCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.RETURN -> handleReturnOCall(EnclaveCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.EXCEPTION -> handleExceptionOCall(EnclaveCallType.fromByte(callTypeID), data)
        }
    }

    /**
     * Handle call initiations from the enclave.
     * This method propagates the call to the appropriate host side call handler. If a return value is produced or an
     * exception occurs, a reply message is sent back to the enclave.
     */
    private fun handleCallOCall(callType: HostCallType, parameterBuffer: ByteBuffer) {
        try {
            val returnBuffer = handleIncomingCall(callType, parameterBuffer)
            /**
             * If there was a non-null return value, send it back to the enclave.
             * If no value is received by the enclave, then [com.r3.conclave.enclave.internal.NativeEnclaveHostInterface.executeOutgoingCall]
             * will return null to the caller on the enclave side.
             */
            if (returnBuffer != null) {
                NativeApi.sendECall(enclaveId, callType.toByte(), CallInterfaceMessageType.RETURN.toByte(), returnBuffer.getAllBytes(avoidCopying = true))
            }
        } catch (throwable: Throwable) {
            val serializedException = ThrowableSerialisation.serialise(throwable)
            NativeApi.sendECall(enclaveId, callType.toByte(), CallInterfaceMessageType.EXCEPTION.toByte(), serializedException)
        }
    }

    /**
     * Handle return messages originating from the enclave.
     */
    private fun handleReturnOCall(callType: EnclaveCallType, returnBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.last().returnBuffer = ByteBuffer.wrap(returnBuffer.getAllBytes())
    }

    /**
     * Handle exception messages originating from the enclave.
     */
    private fun handleExceptionOCall(callType: EnclaveCallType, exceptionBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.last().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getAllBytes())
    }
}
