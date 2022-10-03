package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveStartException
import com.r3.conclave.common.internal.*
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.getAllBytes
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor

/**
 * This class is a streaming implementation of the [HostCallInterface].
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the host, see [com.r3.conclave.common.internal.CallInitiator]
 *  - Route calls from the host to the appropriate enclave side call handler, see [com.r3.conclave.common.internal.CallAcceptor]
 *  - Handle the low-level details of the messaging protocol (streamed ecalls and ocalls).
 */
class StreamHostCallInterface(
        private val inputStream: InputStream,
        private val outputStream: OutputStream,
        private val enclaveCallThreadPool: ThreadPoolExecutor
) : HostCallInterface() {
    private val receiveLoop = object : Runnable {
        var done = false
        override fun run() {
            while (!done) {
                try {
                    handleMessage(StreamCallInterfaceMessage.readFromStream(inputStream))
                } catch (e: InterruptedException) {
                    done = true
                    break
                }
            }
        }
    }

    /**
     * Place the message received asynchronously from the enclave and put it on the top of the stack
     * for the thread from which it originated.
     */
    fun handleMessage(message: StreamCallInterfaceMessage) {
        val currentStack = checkNotNull(threadLocalStacks[message.sourceThreadID]) {
            "Host call may not occur outside the context of an enclave call."
        }
        currentStack.peek().message = message
        currentStack.peek().callCompletionSemaphore.release()
    }

    /**
     * Here we start the message receive loop.
     */
    private val receiveLoopThread = Thread(receiveLoop).apply { start() }

    /**
     * And here we stop it by setting the loop condition false and interrupting the thread.
     */
    fun stop() {
        receiveLoop.done = true
        receiveLoopThread.interrupt()
        receiveLoopThread.join()
    }

    /**
     * The message receive loop uses a semaphore
     */
    private inner class StackFrame(
            val targetThreadID: Long,
            val callType: HostCallType,
            var message: StreamCallInterfaceMessage? = null,
            val callCompletionSemaphore: Semaphore = Semaphore(0))

    /**
     * Each thread has a lazily created stack which contains a frame for the currently active host call.
     * When a message arrives from the host, this stack is used to associate the return value with the corresponding call.
     */
    private val threadLocalStacks = ConcurrentHashMap<Long, Stack<StackFrame>>()
    private val stack: Stack<StackFrame>
        get() = threadLocalStacks.computeIfAbsent(Thread.currentThread().id) { Stack<StackFrame>() }

    private fun checkCallType(type: HostCallType) = check(type == stack.peek().callType) { "Call type mismatch" }

    /**
     * Internal method for initiating a host call with specific arguments.
     * This should not be called directly, but instead by implementations in [HostCallInterface].
     */
    override fun executeCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.push(StackFrame(-1, callType))

        val outgoingMessage = StreamCallInterfaceMessage(
                targetThreadID = Thread.currentThread().id,
                sourceThreadID = -1,
                callTypeID = callType.toShort(),
                messageTypeID = parameterBuffer.get(),
                payload = parameterBuffer.getAllBytes(avoidCopying = true))

        outgoingMessage.writeToStream(outputStream)
        outputStream.flush()

        /**
         * We could handle call messages by starting a new thread for each one, but this would lead to a lot of
         * idle threads during recursive calls. So instead we handle call messages here so that the calling thread is
         * re-used during recursive calls.
         */
        do {
            stack.peek().callCompletionSemaphore.acquire()
            val replyMessage = checkNotNull(stack.peek().message) { "Message is missing from stack frame." }
            val messageType = CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)
            if (messageType == CallInterfaceMessageType.CALL) {
                val replyCallType = EnclaveCallType.fromShort(replyMessage.callTypeID)
                val replyCallParameterBytes = checkNotNull(replyMessage.payload) { "Calls must have parameter bytes." }
                handleCallMessage(replyCallType, replyCallParameterBytes)
            }
        } while(messageType == CallInterfaceMessageType.CALL)

        val replyMessage = checkNotNull(stack.peek().message)

        /** Sanity check to ensure that the reply call type matches the outgoing call type */
        checkCallType(HostCallType.fromShort(replyMessage.callTypeID))

        val messageType = CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)
        val messageBytes = replyMessage.payload

        stack.pop()

        return when (messageType) {
            CallInterfaceMessageType.CALL -> throw IllegalStateException()  // Calls should already have been handled!
            CallInterfaceMessageType.RETURN -> messageBytes?.let { ByteBuffer.wrap(it) }
            CallInterfaceMessageType.EXCEPTION -> throw ThrowableSerialisation.deserialise(messageBytes!!)
        }
    }

    /** In release mode we want to sanitise exceptions to prevent leakage of information from the enclave */
    var sanitiseExceptions: Boolean = false

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
     * Handle a call message.
     */
    private fun handleCallMessage(callType: EnclaveCallType, parameterBytes: ByteArray) {
        val parameterBuffer = ByteBuffer.wrap(parameterBytes).asReadOnlyBuffer()

        val replyMessage = try {
            val returnBuffer = acceptCall(callType, parameterBuffer)
            StreamCallInterfaceMessage(
                    -1,
                    Thread.currentThread().id,
                    callType.toShort(),
                    CallInterfaceMessageType.RETURN.toByte(),
                    returnBuffer?.getAllBytes())
        } catch (throwable: Throwable) {
            val maybeSanitisedThrowable = if (sanitiseExceptions) sanitiseThrowable(throwable) else throwable
            val serialisedThrowable = ThrowableSerialisation.serialise(maybeSanitisedThrowable)
            StreamCallInterfaceMessage(
                    -1,
                    Thread.currentThread().id,
                    callType.toShort(),
                    CallInterfaceMessageType.RETURN.toByte(),
                    serialisedThrowable)
        }

        replyMessage.writeToStream(outputStream)
        outputStream.flush()
    }
}
