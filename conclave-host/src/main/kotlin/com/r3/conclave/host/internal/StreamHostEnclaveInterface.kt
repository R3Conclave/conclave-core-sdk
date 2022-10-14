package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.mail.internal.readInt
import com.r3.conclave.utilities.internal.getAllBytes
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
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
        toEnclaveRaw: OutputStream,
        fromEnclaveRaw: InputStream
) : HostEnclaveInterface(), Closeable {
    private val toEnclave = DataOutputStream(toEnclaveRaw)
    private val fromEnclave = DataInputStream(fromEnclaveRaw)

    /** Represents the lifecycle of the interface. */
    private enum class State {
        READY,
        RUNNING,
        STOPPED
    }

    private var state = State.READY

    private var maxConcurrentCalls = 0
    private val callGuardSemaphore = Semaphore(0)

    private val receiveLoop = object : Runnable {
        private var done = false

        /** Receive messages in a loop and send them to the appropriate call context. */
        override fun run() {
            while (!done) {
                val message = receiveMessageFromEnclave()
                when (message.messageType) {
                    StreamCallInterfaceMessageType.STOP -> handleStopMessage()
                    else -> deliverMessageToCallContext(message)
                }
            }
        }

        /** Send the received message to the appropriate call context. */
        private fun deliverMessageToCallContext(message: StreamCallInterfaceMessage) {
            val callContext = checkNotNull(enclaveCallContexts[message.hostThreadID]) {
                "Host call may not occur outside the context of an enclave call."
            }
            callContext.enqueueMessage(message)
        }

        /** The enclave has told us there will be no more messages, shut everything down */
        private fun handleStopMessage() {
            done = true
        }
    }

    private val receiveLoopThread = Thread(receiveLoop, "Host message receive loop")

    /** Start the message receive loop thread and allow calls to begin. */
    fun start() {
        synchronized(state) {
            if (state == State.RUNNING) return
            check(state == State.READY) { "Interface may not be started multiple times." }
            maxConcurrentCalls = fromEnclave.readInt()
            receiveLoopThread.start()
            callGuardSemaphore.release(maxConcurrentCalls)
            state = State.RUNNING
        }
    }

    /** Shut down both the host and the enclave call interface. */
    override fun close() {
        /**
         * Prevent new calls from entering the interface. This has to be synchronized to avoid race condition between
         * the semaphore acquisition and the lockout check in [executeOutgoingCall].
         */
        synchronized(state) {
            if (state == State.STOPPED) return
            check(state == State.RUNNING) { "Call interface is not running." }
            state = State.STOPPED
        }

        /** Wait for any pending calls to finish, this is released when a call context is retired. */
        callGuardSemaphore.acquireUninterruptibly(maxConcurrentCalls)

        /** Stop enclave and host receive loops */
        sendMessageToEnclave(StreamCallInterfaceMessage.STOP_MESSAGE)
        receiveLoopThread.join()
    }

    /**
     * Send a message to the receiving thread on the enclave side interface.
     * Once received, these messages are routed to the appropriate call context in order to support concurrency.
     * The synchronisation that occurs in this method (and the corresponding message on the enclave side) is the primary
     * bottleneck of this call interface implementation.
     */
    private fun sendMessageToEnclave(message: StreamCallInterfaceMessage) {
        val messageBytes = message.toByteArray()
        synchronized(toEnclave) {
            toEnclave.writeIntLengthPrefixBytes(messageBytes)
            toEnclave.flush()
        }
    }

    /** Blocks until a message is received from the enclave. */
    private fun receiveMessageFromEnclave(): StreamCallInterfaceMessage {
        return StreamCallInterfaceMessage.fromByteArray(fromEnclave.readIntLengthPrefixBytes())
    }

    /**
     * Because of the way conclave currently works, a host call (enclave->host) never arrives outside the
     * context of a pre-existing enclave call (host->enclave).
     * This class represents that context and any recursive calls that take place within it.
     */
    private inner class EnclaveCallContext {
        private val messageQueue = ArrayBlockingQueue<StreamCallInterfaceMessage>(4)
        private var activeCalls = 0

        fun hasActiveCalls(): Boolean = (activeCalls > 0)

        fun enqueueMessage(message: StreamCallInterfaceMessage) = messageQueue.put(message)

        fun sendMessage(messageType: StreamCallInterfaceMessageType, callTypeID: Byte, payload: ByteBuffer?) {
            val outgoingMessage = StreamCallInterfaceMessage(
                    Thread.currentThread().id, messageType, callTypeID, payload?.getAllBytes(avoidCopying = true))

            sendMessageToEnclave(outgoingMessage)
        }

        fun sendCallMessage(callType: EnclaveCallType, parameterBuffer: ByteBuffer?) {
            requireNotNull(parameterBuffer)
            sendMessage(StreamCallInterfaceMessageType.CALL, callType.toByte(), parameterBuffer)
        }

        fun sendReturnMessage(callType: HostCallType, returnBytes: ByteBuffer?) {
            sendMessage(StreamCallInterfaceMessageType.RETURN, callType.toByte(), returnBytes)
        }

        fun sendExceptionMessage(callType: HostCallType, exceptionBuffer: ByteBuffer?) {
            requireNotNull(exceptionBuffer)
            sendMessage(StreamCallInterfaceMessageType.EXCEPTION, callType.toByte(), exceptionBuffer)
        }

        fun handleCallMessage(callMessage: StreamCallInterfaceMessage) {
            val messageType = callMessage.messageType
            require(messageType == StreamCallInterfaceMessageType.CALL)

            val callType = HostCallType.fromByte(callMessage.callTypeID)
            val parameterBuffer = checkNotNull(callMessage.payload) { "Received call message without parameter bytes." }

            val returnBuffer = try {
                handleIncomingCall(callType, ByteBuffer.wrap(parameterBuffer))
            } catch (t: Throwable) {
                sendExceptionMessage(callType, ByteBuffer.wrap(ThrowableSerialisation.serialise(t)))
                return
            }

            sendReturnMessage(callType, returnBuffer)
        }

        private fun initiateCallInternal(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
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

            check(EnclaveCallType.fromByte(replyMessage.callTypeID) == callType)
            check(replyMessageType != StreamCallInterfaceMessageType.CALL)

            if (replyMessageType == StreamCallInterfaceMessageType.EXCEPTION) {
                checkNotNull(replyPayload) { "Received exception message without parameter bytes." }
                throw ThrowableSerialisation.deserialise(replyPayload)
            }

            return replyPayload?.let { ByteBuffer.wrap(replyPayload) }
        }

        /** Here we keep track of the number of times [initiateCall] has been re-entered. */
        fun initiateCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
            activeCalls++
            try {
                return initiateCallInternal(callType, parameterBuffer)
            } finally {
                activeCalls--
            }
        }
    }

    /**
     * Thread local enclave call contexts.
     * We don't use [ThreadLocal] here because the call context for an arbitrary call has to be
     * reachable from the message receive thread.
     */
    private val enclaveCallContexts = ConcurrentHashMap<Long, EnclaveCallContext>()

    /**
     * Internal method for initiating an enclave call with specific arguments.
     * This should not be called directly, but instead by implementations in [HostEnclaveInterface].
     */
    override fun executeOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        val threadID = Thread.currentThread().id

        if (!enclaveCallContexts.containsKey(threadID)) {
            synchronized(state) {
                check(state == State.RUNNING) { "Call interface is not running." }
                callGuardSemaphore.acquireUninterruptibly()
            }
        }

        val enclaveCallContext = enclaveCallContexts.computeIfAbsent(threadID) {
            EnclaveCallContext()
        }

        try {
            return enclaveCallContext.initiateCall(callType, parameterBuffer)
        } finally {
            if (!enclaveCallContext.hasActiveCalls()) {
                enclaveCallContexts.remove(threadID)
                callGuardSemaphore.release()
            }
        }
    }
}
