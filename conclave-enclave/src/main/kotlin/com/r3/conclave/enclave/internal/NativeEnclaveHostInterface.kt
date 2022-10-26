package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveStartException
import com.r3.conclave.common.internal.*
import com.r3.conclave.enclave.internal.EnclaveUtils.sanitiseThrowable
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer
import kotlin.collections.ArrayDeque

typealias StackFrame = CallInterfaceStackFrame<HostCallType>

/**
 * This class is the implementation of the [EnclaveHostInterface] for native enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the host, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the host to the appropriate enclave side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (ECalls and OCalls).
 */
class NativeEnclaveHostInterface : EnclaveHostInterface() {
    /** In release mode we want to sanitise exceptions to prevent leakage of information from the enclave */
    var sanitiseExceptions: Boolean = false

    /**
     * Each thread has a lazily created stack which contains a frame for the currently active host call.
     * When a message arrives from the host, this stack is used to associate the return value with the corresponding call.
     */
    private val threadLocalStacks = ThreadLocal.withInitial { ArrayDeque<StackFrame>() }
    private val stack get() = threadLocalStacks.get()

    private fun checkCallType(type: HostCallType) = check(type == stack.last().callType) { "Call type mismatch" }

    /**
     * Internal method for initiating a host call with specific arguments.
     * This should not be called directly, but instead by implementations in [EnclaveHostInterface].
     */
    override fun executeOutgoingCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        val stackFrame = StackFrame(callType, null, null)
        stack.addLast(stackFrame)

        Native.jvmOCall(
                callType.toByte(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer.getAllBytes(avoidCopying = true))

        /** If the stack frame is not the one we pushed earlier, something funky has happened! */
        check(stackFrame === stack.removeLast()) {
            "Wrong stack frame popped during host call, something isn't right!"
        }

        if (stack.isEmpty()) {
            threadLocalStacks.remove()
        }

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handle ECalls that originate from the host.
     */
    fun handleECall(callTypeID: Byte, messageType: CallInterfaceMessageType, data: ByteBuffer) {
        when (messageType) {
            CallInterfaceMessageType.CALL -> handleCallECall(EnclaveCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.RETURN -> handleReturnECall(HostCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.EXCEPTION -> handleExceptionECall(HostCallType.fromByte(callTypeID), data)
        }
    }

    /**
     * Handle call initiations from the host.
     * This method propagates the call to the appropriate enclave side call handler. If a return value is produced or an
     * exception occurs, a reply message is sent back to the host.
     */
    private fun handleCallECall(callType: EnclaveCallType, parameterBuffer: ByteBuffer) {
        try {
            val returnBuffer = handleIncomingCall(callType, parameterBuffer)
            /**
             * If there was a non-null return value, send it back to the host.
             * If no value is received by the host, then [com.r3.conclave.host.internal.NativeHostEnclaveInterface.executeOutgoingCall]
             * will return null to the caller on the host side.
             */
            if (returnBuffer != null) {
                Native.jvmOCall(callType.toByte(), CallInterfaceMessageType.RETURN.toByte(), returnBuffer.getAllBytes(avoidCopying = true))
            }
        } catch (throwable: Throwable) {
            val maybeSanitisedThrowable = if (sanitiseExceptions) sanitiseThrowable(throwable) else throwable
            val serializedException = ThrowableSerialisation.serialise(maybeSanitisedThrowable)
            Native.jvmOCall(callType.toByte(), CallInterfaceMessageType.EXCEPTION.toByte(), serializedException)
        }
    }

    /**
     * Handle return messages originating from the host.
     */
    private fun handleReturnECall(callType: HostCallType, returnBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.last().returnBuffer = ByteBuffer.wrap(returnBuffer.getAllBytes())
    }

    /**
     * Handle exception messages originating from the host.
     */
    private fun handleExceptionECall(callType: HostCallType, exceptionBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.last().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getAllBytes())
    }
}
