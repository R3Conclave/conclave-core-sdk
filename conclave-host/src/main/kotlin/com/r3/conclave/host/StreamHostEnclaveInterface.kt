package com.r3.conclave.host

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.internal.HostEnclaveInterface
import com.r3.conclave.utilities.internal.getAllBytes
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * This class is a streaming implementation of the [EnclaveCallInterface].
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInitiator]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallAcceptor]
 *  - Handle the low-level details of the messaging protocol (streamed ecalls and ocalls).
 */
class StreamHostEnclaveInterface(
        private val outputStream: OutputStream,     // Messages going to the enclave
        private val inputStream: InputStream        // Messages arriving from the enclave
) : HostEnclaveInterface() {
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
        val currentStack = checkNotNull(threadLocalStacks[message.targetThreadID]) {
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
            val callType: EnclaveCallType,
            var message: StreamCallInterfaceMessage? = null,
            val callCompletionSemaphore: Semaphore = Semaphore(0))

    /**
     * Each thread has a lazily created stack which contains a frame for the currently active enclave call.
     * When a message arrives from the enclave, this stack is used to associate the return value with the corresponding call.
     */
    private val threadLocalStacks = ConcurrentHashMap<Long, Stack<StackFrame>>()
    private val stack: Stack<StackFrame>
        get() = threadLocalStacks.computeIfAbsent(Thread.currentThread().id) { Stack<StackFrame>() }

    private fun checkCallType(type: EnclaveCallType) = check(type == stack.peek().callType) { "Call type mismatch" }

    /**
     * Internal method for initiating an enclave call with specific arguments.
     * This should not be called directly, but instead by implementations in [EnclaveCallInterface].
     */
    override fun initiateOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        stack.push(StackFrame(callType))

        val outgoingMessage = StreamCallInterfaceMessage(
                sourceThreadID = Thread.currentThread().id,
                targetThreadID = -1,
                callTypeID = callType.toByte(),
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
                val replyCallType = HostCallType.fromByte(replyMessage.callTypeID)
                val replyCallParameterBytes = checkNotNull(replyMessage.payload) { "Calls must have parameter bytes." }
                handleCallMessage(replyCallType, replyCallParameterBytes)
            }
        } while(messageType == CallInterfaceMessageType.CALL)

        val replyMessage = checkNotNull(stack.peek().message)

        /** Sanity check to ensure that the reply call type matches the outgoing call type */
        checkCallType(EnclaveCallType.fromByte(replyMessage.callTypeID))

        val messageType = CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)
        val messageBytes = replyMessage.payload

        stack.pop()

        return when (messageType) {
            CallInterfaceMessageType.CALL -> throw IllegalStateException()  // Calls should already have been handled!
            CallInterfaceMessageType.RETURN -> messageBytes?.let { ByteBuffer.wrap(it) }
            CallInterfaceMessageType.EXCEPTION -> throw ThrowableSerialisation.deserialise(messageBytes!!)
        }
    }

    /**
     * Handle a call message.
     */
    private fun handleCallMessage(callType: HostCallType, parameterBytes: ByteArray) {
        val parameterBuffer = ByteBuffer.wrap(parameterBytes).asReadOnlyBuffer()

        val replyMessage = try {
            val returnBuffer = handleIncomingCall(callType, parameterBuffer)
            StreamCallInterfaceMessage(
                    -1,
                    Thread.currentThread().id,
                    callType.toByte(),
                    CallInterfaceMessageType.RETURN.toByte(),
                    returnBuffer?.getAllBytes())
        } catch (throwable: Throwable) {
            val serializedException = ThrowableSerialisation.serialise(throwable)
            StreamCallInterfaceMessage(
                    -1,
                    Thread.currentThread().id,
                    callType.toByte(),
                    CallInterfaceMessageType.RETURN.toByte(),
                    serializedException)
        }

        replyMessage.writeToStream(outputStream)
        outputStream.flush()
    }
}
