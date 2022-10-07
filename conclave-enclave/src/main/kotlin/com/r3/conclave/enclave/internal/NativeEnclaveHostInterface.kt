package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveStartException
import com.r3.conclave.common.internal.*
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer
import java.util.*

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
    private val threadStacks = ThreadLocal<Stack<StackFrame>>()
    private val stack: Stack<StackFrame>
        get() {
            if (threadStacks.get() == null) {
                threadStacks.set(Stack<StackFrame>())
            }
            return threadStacks.get()
        }

    private fun checkCallType(type: HostCallType) = check(type == stack.peek().callType) { "Call type mismatch" }

    /**
     * Internal method for initiating a host call with specific arguments.
     * This should not be called directly, but instead by implementations in [EnclaveHostInterface].
     */
    override fun executeOutgoingCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.push(StackFrame(callType, null, null))

        Native.jvmOCall(
                callType.toByte(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer.getAllBytes(avoidCopying = true))

        val stackFrame = stack.pop()

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handle ecalls that originate from the host.
     */
    fun handleECall(callTypeID: Byte, messageType: CallInterfaceMessageType, data: ByteBuffer) {
        when (messageType) {
            CallInterfaceMessageType.CALL -> handleCallECall(EnclaveCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.RETURN -> handleReturnECall(HostCallType.fromByte(callTypeID), data)
            CallInterfaceMessageType.EXCEPTION -> handleExceptionECall(HostCallType.fromByte(callTypeID), data)
        }
    }

    /**
     * In release mode we want exceptions propagated out of the enclave to be sanitised
     * to reduce the likelihood of secrets being leaked from the enclave.
     */
    private fun sanitiseThrowable(throwable: Throwable): Throwable {
        return when (throwable) {
            is EnclaveStartException -> throwable
            // Release enclaves still need to notify the host if they were unable to decrypt mail, but there's
            // no need to include the message or stack trace in case any secrets can be inferred from them.
            is MailDecryptionException -> MailDecryptionException()
            else -> {
                RuntimeException("Release enclave threw an exception which was swallowed to avoid leaking any secrets")
            }
        }
    }

    /**
     * Handle call initiations from the host.
     * This method propagates the call to the appropriate enclave side call handler. If a return value is produced or an
     * exception occurs, a reply message is sent back to the host.
     */
    private fun handleCallECall(callType: EnclaveCallType, parameterBuffer: ByteBuffer) {
        try {
            handleIncomingCall(callType, parameterBuffer)?.let {
                /**
                 * If there was a non-null return value, send it back to the host.
                 * If no value is received by the host, then [com.r3.conclave.host.internal.NativeHostEnclaveInterface.executeOutgoingCall]
                 * will return null to the caller on the host side.
                 */
                Native.jvmOCall(callType.toByte(), CallInterfaceMessageType.RETURN.toByte(), it.getAllBytes(avoidCopying = true))
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
        stack.peek().returnBuffer = ByteBuffer.wrap(returnBuffer.getAllBytes())
    }

    /**
     * Handle exception messages originating from the host.
     */
    private fun handleExceptionECall(callType: HostCallType, exceptionBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.peek().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getAllBytes())
    }
}
