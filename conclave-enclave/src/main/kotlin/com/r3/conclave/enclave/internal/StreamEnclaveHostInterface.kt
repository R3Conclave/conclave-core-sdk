package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.mail.internal.writeInt
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

    /** On startup, send the maximum number of concurrent calls to the host. */
    init {
        outputStream.writeInt(maximumConcurrentCalls)
    }

    private val receiveLoop = object : Runnable {
        private var done = false

        /** Receive messages in a loop and send them to the appropriate call context. */
        override fun run() {
            while (!done) {
                when (StreamCallInterfaceThreadCommand.fromByte(inputStream.read().toByte())) {
                    StreamCallInterfaceThreadCommand.MESSAGE -> handleMessageCommand()
                    StreamCallInterfaceThreadCommand.STOP -> handleStopCommand()
                }
            }
        }

        /** Send the received message to the appropriate call context. */
        private fun handleMessageCommand() {
            val message = StreamCallInterfaceMessage.readFromStream(inputStream)
            val callContext = enclaveCallContexts.computeIfAbsent(message.hostThreadID) {
                val newCallContext = EnclaveCallContext(message.hostThreadID)
                callExecutor.execute(newCallContext)
                newCallContext
            }
            callContext.enqueMessage(message)
        }

        /** The host has told us there will be no more messages, shut everything down. */
        private fun handleStopCommand() {
            callExecutor.shutdown()     // Finish processing any outstanding calls
            sendStopCommandToHost()     // Let the host know there will be no more messages
            done = true                 // Terminate the loop
        }
    }

    /** Start the message receive loop thread. */
    private val receiveLoopThread = Thread(receiveLoop, "Enclave message receive loop").apply { start() }

    /**
     * The shutdown process starts on the host.
     * This function just blocks until the host sends a stop message and the message receive loop terminates.
     */
    fun awaitStop() {
        receiveLoopThread.join()
    }

    /** Send a message to the receiving thread in the enclave-host interface. */
    private fun sendMessageToHost(message: StreamCallInterfaceMessage) {
        synchronized(outputStream) {
            outputStream.write(StreamCallInterfaceThreadCommand.MESSAGE.toByte().toInt())
            message.writeToStream(outputStream)
        }
    }

    /** Send a stop command to the receiving thread in the enclave-host interface. */
    private fun sendStopCommandToHost() {
        synchronized(outputStream) {
            outputStream.write(StreamCallInterfaceThreadCommand.STOP.toByte().toInt())
        }
    }

    private inner class EnclaveCallContext(
            private val hostThreadID: Long,
    ): Runnable {
        private val messageQueue = ArrayBlockingQueue<StreamCallInterfaceMessage>(4)

        fun enqueMessage(message: StreamCallInterfaceMessage) = messageQueue.put(message)

        fun sendMessage(callTypeID: Byte, messageTypeID: Byte, payload: ByteBuffer?) {
            val outgoingMessage = StreamCallInterfaceMessage(
                    hostThreadID, callTypeID, messageTypeID, payload?.getAllBytes(avoidCopying = true))

            sendMessageToHost(outgoingMessage)
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
            val parameterBytes = checkNotNull(callMessage.payload) { "Received call message without parameter bytes." }

            val returnBuffer = try {
                handleIncomingCall(callType, ByteBuffer.wrap(parameterBytes))
            } catch (t: Throwable) {
                sendExceptionMessage(callType, ByteBuffer.wrap(ThrowableSerialisation.serialise(t)))
                return
            }

            sendReturnMessage(callType, returnBuffer)
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
            try {
                threadLocalCallContext.set(this)
                handleCallMessage(messageQueue.take())
            } finally {
                threadLocalCallContext.remove()
                enclaveCallContexts.remove(hostThreadID)
            }
        }
    }

    /**
     * This maps host thread IDs to enclave side call contexts.
     * This map is used in the message receive loop to route messages to the appropriate call context.
     */
    private val enclaveCallContexts = ConcurrentHashMap<Long, EnclaveCallContext>()

    /**
     * This contains the call context for the current thread.
     * These are the same object instances that are contained in the map above.
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
