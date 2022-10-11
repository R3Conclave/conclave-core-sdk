package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.getAllBytes
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class StreamEnclaveHostInterface(
        private val outputStream: OutputStream,     // Messages going to the host
        private val inputStream: InputStream,       // Messages arriving from the host
        maximumConcurrentCalls: Int
) : EnclaveHostInterface() {
    private val callExecutor = Executors.newFixedThreadPool(maximumConcurrentCalls)

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
            val callContext = enclaveCallContexts.computeIfAbsent(message.hostThreadID) {
                val newCallContext = EnclaveCallContext(message.hostThreadID)
                callExecutor.execute(newCallContext)
                newCallContext
            }
            callContext.enqueMessage(message)
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

    private inner class EnclaveCallContext(
            private val hostThreadID: Long,
    ): Runnable {
        private val messageQueue = ArrayBlockingQueue<StreamCallInterfaceMessage>(4)

        fun enqueMessage(message: StreamCallInterfaceMessage) = messageQueue.add(message)

        fun sendMessage(callTypeID: Byte, messageTypeID: Byte, payload: ByteBuffer?) {
            val outgoingMessage = StreamCallInterfaceMessage(
                    hostThreadID, callTypeID, messageTypeID, payload?.getAllBytes(avoidCopying = true))

            synchronized(outputStream) {
                outgoingMessage.writeToStream(outputStream)
                outputStream.flush()
            }
        }

        fun sendCallMessage(callType: HostCallType, parameterBuffer: ByteBuffer?) {
            requireNotNull(parameterBuffer)
            sendMessage(callType.toByte(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer)
        }

        fun sendReturnMessage(callType: EnclaveCallType, returnBytes: ByteBuffer?) {
            sendMessage(callType.toByte(), CallInterfaceMessageType.RETURN.toByte(), returnBytes)
        }

        fun sendExceptionMessage(callType: EnclaveCallType, exceptionBuffer: ByteBuffer?) {
            requireNotNull(exceptionBuffer)
            sendMessage(callType.toByte(), CallInterfaceMessageType.EXCEPTION.toByte(), exceptionBuffer)
        }

        fun handleCallMessage(callMessage: StreamCallInterfaceMessage) {
            val messageType = CallInterfaceMessageType.fromByte(callMessage.messageTypeID)
            require(messageType == CallInterfaceMessageType.CALL)

            val callType = EnclaveCallType.fromByte(callMessage.callTypeID)
            val parameterBuffer = checkNotNull(callMessage.payload) { "Received call message without parameter bytes." }

            try {
                val returnBuffer = handleIncomingCall(callType, ByteBuffer.wrap(parameterBuffer))
                sendReturnMessage(callType, returnBuffer)
            } catch (t: Throwable) {
                sendExceptionMessage(callType, ByteBuffer.wrap(ThrowableSerialisation.serialise(t)))
            }
        }

        fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
            sendCallMessage(callType, parameterBuffer)

            /** Iterate, handling CALL messages until a message that is not a CALL arrives */
            var replyMessage: StreamCallInterfaceMessage
            while (true) {
                replyMessage = messageQueue.take()
                when (CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)) {
                    CallInterfaceMessageType.CALL -> handleCallMessage(replyMessage)
                    else -> break
                }
            }

            val replyMessageType = CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)
            val replyPayload = replyMessage.payload

            check(HostCallType.fromByte(replyMessage.callTypeID) == callType)
            check(replyMessageType != CallInterfaceMessageType.CALL)

            if (replyMessageType == CallInterfaceMessageType.EXCEPTION) {
                checkNotNull(replyPayload) { "Received exception message without parameter bytes." }
                throw ThrowableSerialisation.deserialise(replyPayload)
            }

            return replyPayload?.let { ByteBuffer.wrap(replyPayload) }
        }

        /** Handle the initial call */
        override fun run() {
            threadLocalCallContext.set(this)
            handleCallMessage(messageQueue.remove())
            threadLocalCallContext.remove()
        }
    }

    /** This maps call contexts to their host side thread IDs */
    private val enclaveCallContexts = ConcurrentHashMap<Long, EnclaveCallContext>()

    /**
     * This contains the call context for the current thread.
     * These are the same object instances that are contained in the call context map above.
     */
    private val threadLocalCallContext = ThreadLocal<EnclaveCallContext>()

    /**
     * Internal method for initiating a host call with specific arguments.
     * This should not be called directly, but instead by implementations in [EnclaveHostInterface].
     */
    override fun executeOutgoingCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        val callContext = checkNotNull(threadLocalCallContext.get()) {
            "Outgoing host calls may not occur outside the context of an enclave call."
        }
        return callContext.initiateCall(callType, parameterBuffer)
    }
}
