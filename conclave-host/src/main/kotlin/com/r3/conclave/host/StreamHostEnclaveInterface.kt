package com.r3.conclave.host

import com.r3.conclave.common.internal.*
import com.r3.conclave.host.internal.HostEnclaveInterface
import com.r3.conclave.utilities.internal.getAllBytes
import java.awt.TrayIcon
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
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
        private val inputStream: InputStream        // Messages arriving from the enclave
) : HostEnclaveInterface() {
    private val messageReceiveLoop = object : Runnable {
        var done = false

        override fun run() {
            while (!done) {
                try {
                    val message = StreamCallInterfaceMessage.readFromStream(inputStream)
                    deliverMessageToCallExecutor(message)
                } catch (e: InterruptedException) {
                    done = true
                    break
                }
            }
        }

        /**
         * Place the message received asynchronously from the enclave and put it on the top of the stack
         * for the thread from which it originated.
         */
        private fun deliverMessageToCallExecutor(message: StreamCallInterfaceMessage) {
            val currentExecutor = checkNotNull(callExecutors[message.hostThreadID]) {
                "Host call may not occur outside the context of an enclave call."
            }
            currentExecutor.enqueMessage(message)
        }
    }

    /**
     * Here we start the message receive loop.
     */
    private val receiveLoopThread = Thread(messageReceiveLoop).apply { start() }

    /**
     * And here we stop it by setting the loop condition false and interrupting the thread.
     */
    fun stop() {
        messageReceiveLoop.done = true
        receiveLoopThread.interrupt()
        receiveLoopThread.join()
    }

    private val callExecutors = ConcurrentHashMap<Long, CallExecutor>()

    private inner class CallExecutor {
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

            do {
                val message = messageQueue.remove()
                val replyMessage = checkNotNull(message) { "Message is missing from stack frame." }
                val messageType = CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)
                handleCallMessage(message)
            } while(messageType == CallInterfaceMessageType.CALL)

            return null
        }

        fun handleMessage(message: StreamCallInterfaceMessage) {
            when (CallInterfaceMessageType.fromByte(message.messageTypeID)) {
                CallInterfaceMessageType.CALL -> handleCallMessage(message.payload)
                CallInterfaceMessageType.EXCEPTION -> handleExceptionMessage(message)
                CallInterfaceMessageType.RETURN -> handleCallMessage(message)
            }
        }

        fun handleCallMessage() {

        }

        fun handleExceptionMessage(message: StreamCallInterfaceMessage) {

        }

        fun handleReturnMessage(message: StreamCallInterfaceMessage) {

        }
    }

    /**
     * Internal method for initiating an enclave call with specific arguments.
     * This should not be called directly, but instead by implementations in [HostEnclaveInterface].
     */
    override fun initiateOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        val threadID = Thread.currentThread().id
        val callExecutor = callExecutors.computeIfAbsent(threadID) { CallExecutor() }
        return callExecutor.initiateCall(callType, parameterBuffer)
    }
}
