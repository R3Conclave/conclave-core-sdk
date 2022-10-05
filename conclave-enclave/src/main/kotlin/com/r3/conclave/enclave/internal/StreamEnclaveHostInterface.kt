package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor

class StreamEnclaveHostInterface(
        private val outputStream: OutputStream,     // Messages going to the host
        private val inputStream: InputStream,       // Messages arriving from the host
        private val maximumConcurrentCalls: Int
) : EnclaveHostInterface() {
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
        messageReceiveLoop.done = true
        receiveLoopThread.interrupt()
        receiveLoopThread.join()
    }

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
            TODO()
        }
    }

    /**  */
    private val enclaveCallContexts = ConcurrentHashMap<Long, EnclaveCallContext>()

    /**
     * Internal method for initiating a host call with specific arguments.
     * This should not be called directly, but instead by implementations in [EnclaveHostInterface].
     */
    override fun initiateOutgoingCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        TODO("Not yet implemented")
    }
}
