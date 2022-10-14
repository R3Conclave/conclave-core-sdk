package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.mail.internal.writeInt
import com.r3.conclave.utilities.internal.getAllBytes
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class StreamEnclaveHostInterface(
        private val toHost: OutputStream,
        private val fromHost: InputStream,
        private val maximumConcurrentCalls: Int
) : EnclaveHostInterface(), Closeable {

    /** Represents the lifecycle of the interface. */
    private enum class State {
        READY,
        RUNNING,
        STOPPED
    }

    private var state = State.READY
    private val callExecutor = Executors.newFixedThreadPool(maximumConcurrentCalls)

    private val receiveLoop = object : Runnable {
        private var done = false

        /** Receive messages in a loop and send them to the appropriate call context. */
        override fun run() {
            while (!done) {
                val message = StreamCallInterfaceMessage.readFromStream(fromHost)
                when (message.messageType) {
                    StreamCallInterfaceMessageType.STOP -> handleStopMessage()
                    else -> deliverMessageToCallContext(message)
                }
            }
        }

        /** Send the received message to the appropriate call context. */
        private fun deliverMessageToCallContext(message: StreamCallInterfaceMessage) {
            val callContext = enclaveCallContexts.computeIfAbsent(message.hostThreadID) {
                val newCallContext = EnclaveCallContext(message.hostThreadID)
                callExecutor.execute(newCallContext)
                newCallContext
            }
            callContext.enqueueMessage(message)
        }

        /** The host has told us there will be no more messages, shut everything down. */
        private fun handleStopMessage() {
            callExecutor.shutdown()
            sendMessageToHost(StreamCallInterfaceMessage.STOP_MESSAGE)
            done = true
        }
    }

    /** Start the message receive loop thread. */
    private val receiveLoopThread = Thread(receiveLoop, "Enclave message receive loop")

    fun start() {
        synchronized(state) {
            if (state == State.RUNNING) return
            check(state == State.READY) { "Call interface may not be started multiple times." }
            toHost.writeInt(maximumConcurrentCalls)
            toHost.flush()
            receiveLoopThread.start()
            state = State.RUNNING
        }
    }

    /**
     * The shutdown process starts on the host.
     * This function just blocks until the host sends a stop message and the message receive loop terminates.
     */
    override fun close() {
        synchronized(state) {
            if (state == State.STOPPED) return
            check(state == State.RUNNING) { "Call interface is not running." }
            receiveLoopThread.join()
            state = State.STOPPED
        }
    }

    /**
     * Send a message to the receiving thread on the host side interface.
     * Once received, these messages are routed to the appropriate call context in order to support concurrency.
     * The synchronisation that occurs in this method (and the corresponding message on the host side) is the primary
     * bottleneck of this call interface implementation.
     */
    private fun sendMessageToHost(message: StreamCallInterfaceMessage) {
        synchronized(toHost) {
            message.writeToStream(toHost)
            toHost.flush()
        }
    }

    private inner class EnclaveCallContext(
            private val hostThreadID: Long,
    ): Runnable {
        private val messageQueue = ArrayBlockingQueue<StreamCallInterfaceMessage>(4)

        fun enqueueMessage(message: StreamCallInterfaceMessage) = messageQueue.put(message)

        fun sendMessage(messageType: StreamCallInterfaceMessageType, callTypeID: Byte, payload: ByteBuffer?) {
            val outgoingMessage = StreamCallInterfaceMessage(
                    hostThreadID, messageType, callTypeID, payload?.getAllBytes(avoidCopying = true))

            sendMessageToHost(outgoingMessage)
        }

        fun sendCallMessage(callType: HostCallType, parameterBuffer: ByteBuffer?) {
            requireNotNull(parameterBuffer)
            sendMessage(StreamCallInterfaceMessageType.CALL, callType.toByte(), parameterBuffer)
        }

        fun sendReturnMessage(callType: EnclaveCallType, returnBytes: ByteBuffer?) {
            sendMessage(StreamCallInterfaceMessageType.RETURN, callType.toByte(), returnBytes)
        }

        fun sendExceptionMessage(callType: EnclaveCallType, exceptionBuffer: ByteBuffer?) {
            requireNotNull(exceptionBuffer)
            sendMessage(StreamCallInterfaceMessageType.EXCEPTION, callType.toByte(), exceptionBuffer)
        }

        fun handleCallMessage(callMessage: StreamCallInterfaceMessage) {
            val messageType = callMessage.messageType
            require(messageType == StreamCallInterfaceMessageType.CALL)

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
                when (replyMessage.messageType) {
                    StreamCallInterfaceMessageType.CALL -> handleCallMessage(replyMessage)
                    else -> break
                }
            }

            val replyMessageType = replyMessage.messageType
            val replyPayload = replyMessage.payload

            check(HostCallType.fromByte(replyMessage.callTypeID) == callType)
            check(replyMessageType != StreamCallInterfaceMessageType.CALL)

            if (replyMessageType == StreamCallInterfaceMessageType.EXCEPTION) {
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
