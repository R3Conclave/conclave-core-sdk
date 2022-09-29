package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveStartException
import com.r3.conclave.common.internal.EnclaveCallType
import com.r3.conclave.common.internal.HostCallType
import com.r3.conclave.common.internal.NativeMessageType
import com.r3.conclave.common.internal.ThrowableSerialisation
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.getAllBytes
import java.nio.ByteBuffer
import java.util.*

class NativeHostCallInterface : HostCallInterface() {
    var sanitiseExceptions: Boolean = false

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

    override fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.push(StackFrame(callType, null, null))

        Native.jvmOcall(
                callType.toShort(), NativeMessageType.CALL.toByte(), parameterBuffer.getAllBytes(avoidCopying = true))

        val stackFrame = stack.pop()

        stackFrame.exceptionBuffer?.let {
            throw ThrowableSerialisation.deserialise(it)
        }

        return stackFrame.returnBuffer
    }

    /**
     * Handle Ecalls that originate from the host.
     */
    fun handleEcall(callTypeID: Short, ecallType: NativeMessageType, data: ByteBuffer) {
        when (ecallType) {
            NativeMessageType.CALL -> handleCallEcall(EnclaveCallType.fromShort(callTypeID), data)
            NativeMessageType.RETURN -> handleReturnEcall(HostCallType.fromShort(callTypeID), data)
            NativeMessageType.EXCEPTION -> handleExceptionEcall(HostCallType.fromShort(callTypeID), data)
        }
    }

    /**
     * In release mode we want exceptions propagated out of the enclave to be sanitised
     * to reduce the likelihood of secrets being leaked out of the enclave.
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

    private fun handleCallEcall(callType: EnclaveCallType, parameterBuffer: ByteBuffer) {
        try {
            acceptCall(callType, parameterBuffer)?.let {
                Native.jvmOcall(
                        callType.toShort(), NativeMessageType.RETURN.toByte(), it.getAllBytes(avoidCopying = true))
            }
        } catch (throwable: Throwable) {
            val maybeSanitisedThrowable = if (sanitiseExceptions) sanitiseThrowable(throwable) else throwable
            val serializedException = ThrowableSerialisation.serialise(maybeSanitisedThrowable)
            Native.jvmOcall(callType.toShort(), NativeMessageType.EXCEPTION.toByte(), serializedException)
        }
    }

    private fun handleReturnEcall(callType: HostCallType, returnBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Return Ecall type mismatch." }
        stack.peek().returnBuffer = ByteBuffer.wrap(returnBuffer.getAllBytes())
    }

    private fun handleExceptionEcall(callType: HostCallType, exceptionBuffer: ByteBuffer) {
        check(callType == stack.peek().callType) { "Exception Ecall type mismatch." }
        stack.peek().exceptionBuffer = ByteBuffer.wrap(exceptionBuffer.getAllBytes())
    }
}
