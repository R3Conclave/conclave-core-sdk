package com.r3.conclave.host

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.internal.HostEnclaveInterface
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * This class is a streaming implementation of the [HostEnclaveInterface].
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (streamed ecalls and ocalls).
 */
class StreamHostEnclaveInterface(
        private val outputStream: OutputStream,     // Messages going to the enclave
        private val inputStream: InputStream,       // Messages arriving from the enclave
        private val maximumConcurrentCalls: Int
) : HostEnclaveInterface() {
    /**
     * Message receive loop runnable.
     */
    private val messageReceiveLoop = object : Runnable {
        @Volatile
        var done = false

        /** Receive messages in a loop and send them to the appropriate call context. */
        override fun run() {
            while (!done) {
                val message = try {
                    StreamCallInterfaceMessage.readFromStream(inputStream)
                } catch (e: InterruptedException) {
                    done = true
                    break
                }
                deliverMessageToCallContext(message)
            }
        }

        /** Send the received message to the appropriate call context. */
        private fun deliverMessageToCallContext(message: StreamCallInterfaceMessage) {
            val currentExecutor = checkNotNull(enclaveCallContexts[message.hostThreadID]) {
                "Host call may not occur outside the context of an enclave call."
            }
            currentExecutor.enqueMessage(message)
        }
    }

    /** Start the message receive loop thread. */
    private val receiveLoopThread = Thread(messageReceiveLoop).apply { start() }

    /** Stop the message receive loop thread. */
    fun stop() {
        concurrentCallSemaphore.acquire(maximumConcurrentCalls)
        messageReceiveLoop.done = true
        receiveLoopThread.interrupt()
        receiveLoopThread.join()
    }

    /**
     * Because of the way conclave currently works, a host call (enclave->host) never arrives outside the
     * context of a pre-existing enclave call (host->enclave).
     * This class represents that context and any recursive calls that take place within it.
     */
    private inner class EnclaveCallContext {
        private val messageQueue = ArrayBlockingQueue<StreamCallInterfaceMessage>(4)

        fun enqueMessage(message: StreamCallInterfaceMessage) = messageQueue.add(message)

        fun sendMessage(callTypeID: Byte, messageTypeID: Byte, data: ByteBuffer) {
            val outgoingMessage = StreamCallInterfaceMessage(Thread.currentThread().id, callTypeID, messageTypeID, data.array())
            synchronized(outputStream) {
                outgoingMessage.writeToStream(outputStream)
                outputStream.flush()
            }
        }

        fun initiateCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
            sendMessage(callType.toByte(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer)

            /** Iterate, handling CALL messages until a message that is not a CALL arrives */
            var replyMessage: StreamCallInterfaceMessage
            while(true) {
                replyMessage = messageQueue.remove()
                when(CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)) {
                    CallInterfaceMessageType.CALL -> {
                        val replyCallType = HostCallType.fromByte(replyMessage.callTypeID)
                        val replyPayload = checkNotNull(replyMessage.payload) { "Received call message without parameter bytes." }
                        handleIncomingCall(replyCallType, ByteBuffer.wrap(replyPayload))
                    }
                    else -> break
                }
            }

            val replyMessageType = CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)
            val replyPayload = replyMessage.payload

            check(EnclaveCallType.fromByte(replyMessage.callTypeID) == callType)
            check(replyMessageType != CallInterfaceMessageType.CALL)

            if (replyMessageType == CallInterfaceMessageType.EXCEPTION) {
                checkNotNull(replyPayload) { "Exception message parameter was null." }
                throw ThrowableSerialisation.deserialise(replyPayload)
            }

            return replyPayload?.let { ByteBuffer.wrap(replyPayload) }
        }
    }

    /**
     * Thread local enclave call contexts.
     * We don't use [ThreadLocal] here because the call context for an arbitrary call has to be
     * reachable from the message receive thread.
     */
    private val enclaveCallContexts = ConcurrentHashMap<Long, EnclaveCallContext>()

    /** This is used to wait for all current calls to complete */
    private val concurrentCallSemaphore = Semaphore(maximumConcurrentCalls)

    /**
     * Internal method for initiating an enclave call with specific arguments.
     * This should not be called directly, but instead by implementations in [HostEnclaveInterface].
     */
    override fun initiateOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        concurrentCallSemaphore.acquire()
        val threadID = Thread.currentThread().id
        val enclaveCallContext = enclaveCallContexts.computeIfAbsent(threadID) { EnclaveCallContext() }
        try {
            return enclaveCallContext.initiateCall(callType, parameterBuffer)
        } finally {
            enclaveCallContexts.remove(threadID)
            concurrentCallSemaphore.release()
        }
    }
}
