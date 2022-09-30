package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveStartException
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.CallInterfaceMessageType
import com.r3.conclave.common.internal.ThrowableSerialisation
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer
import java.util.*

/**
 * This class is the implementation of the [HostCallInterface] for native enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the host, see [com.r3.conclave.common.internal.CallInitiator]
 *  - Route calls from the host to the appropriate enclave side call handler, see [com.r3.conclave.common.internal.CallAcceptor]
 *  - Handle the low-level details of the messaging protocol (ecalls and ocalls).
 */
class NativeHostCallInterface : HostCallInterface() {
    /** In release mode we want to sanitise exceptions to prevent leakage of information from the enclave */
    var sanitiseExceptions: Boolean = false

    /**
     * Each thread has a lazily created stack which contains a frame for the currently active host call.
     * When a message arrives from the host, this stack is used to associate the return value with the corresponding call.
     */
    private inner class StackFrame(
            val callType: HostCallType,
            var exceptionBuffer: ByteBuffer?,
            var returnBuffer: ByteBuffer?)

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
     * This should not be called directly, but instead by implementations in [HostCallInterface].
     */
    override fun executeCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.push(StackFrame(callType, null, null))

        Native.jvmOcall(
                callType.toShort(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer.getAllBytes(avoidCopying = true))

        val stackFrame = stack.pop()

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handle ecalls that originate from the host.
     */
    fun handleEcall(callTypeID: Short, ecallType: CallInterfaceMessageType, data: ByteBuffer) {
        when (ecallType) {
            CallInterfaceMessageType.CALL -> handleCallEcall(EnclaveCallType.fromShort(callTypeID), data)
            CallInterfaceMessageType.RETURN -> handleReturnEcall(HostCallType.fromShort(callTypeID), data)
            CallInterfaceMessageType.EXCEPTION -> handleExceptionEcall(HostCallType.fromShort(callTypeID), data)
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
     * This method propagates the call to the appropriate enclave side call handler, then sanitises, serialises and
     * propagates any exceptions that occur. If a return value is produced, a reply message is sent back to the host.
     */
    private fun handleCallEcall(callType: EnclaveCallType, parameterBuffer: ByteBuffer) {
        try {
            acceptCall(callType, parameterBuffer)?.let {
                Native.jvmOcall(callType.toShort(), CallInterfaceMessageType.RETURN.toByte(), it.getAllBytes(avoidCopying = true))
            }
        } catch (throwable: Throwable) {
            val maybeSanitisedThrowable = if (sanitiseExceptions) sanitiseThrowable(throwable) else throwable
            val serializedException = ThrowableSerialisation.serialise(maybeSanitisedThrowable)
            Native.jvmOcall(callType.toShort(), CallInterfaceMessageType.EXCEPTION.toByte(), serializedException)
        }
    }

    /**
     * Handle return messages originating from the host.
     */
    private fun handleReturnEcall(callType: HostCallType, returnBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.peek().returnBuffer = ByteBuffer.wrap(returnBuffer.getAllBytes())
    }

    /**
     * Handle exception messages originating from the host.
     */
    private fun handleExceptionEcall(callType: HostCallType, exceptionBuffer: ByteBuffer) {
        checkCallType(callType)
        stack.peek().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getAllBytes())
    }
}
