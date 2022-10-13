package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.mail.internal.readInt
import com.r3.conclave.utilities.internal.getAllBytes
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
) : HostEnclaveInterface() {
    private var isRunning = true

    /** Receive the number of concurrent calls from the enclave. */
    private val maxConcurrentCalls = inputStream.readInt()
    private val callGuardSemaphore = Semaphore(maxConcurrentCalls)

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
            val callContext = checkNotNull(enclaveCallContexts[message.hostThreadID]) {
                "Host call may not occur outside the context of an enclave call."
            }
            callContext.enqueMessage(message)
        }

        /** The enclave has told us there will be no more messages, shut everything down */
        private fun handleStopCommand() {
            done = true
        }
    }


    /** Stop the message receive loop thread. */
    fun stop() {
        // Prevent any more calls from entering and wait for existing ones to finish
        synchronized(callGuardSemaphore) { isRunning = false }
        callGuardSemaphore.acquire(maxConcurrentCalls)

        // Send a stop command to the other side
        sendStopCommandToEnclave()
        receiveLoopThread.join()
    }

    /** Start the message receive loop thread. */
    private val receiveLoopThread = Thread(receiveLoop, "Host message receive loop").apply { start() }

    /** Send a message to the receiving thread in the enclave-host interface. */
    private fun sendMessageToEnclave(message: StreamCallInterfaceMessage) {
        synchronized(outputStream) {
            outputStream.write(StreamCallInterfaceThreadCommand.MESSAGE.toByte().toInt())
            message.writeToStream(outputStream)
        }
    }

    /** Send a stop command to the receiving thread in the enclave-host interface. */
    private fun sendStopCommandToEnclave() {
        synchronized(outputStream) {
            outputStream.write(StreamCallInterfaceThreadCommand.STOP.toByte().toInt())
        }
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

        fun enqueMessage(message: StreamCallInterfaceMessage) = messageQueue.put(message)

        fun sendMessage(callTypeID: Byte, messageTypeID: Byte, payload: ByteBuffer?) {
            val outgoingMessage = StreamCallInterfaceMessage(
                    Thread.currentThread().id, callTypeID, messageTypeID, payload?.getAllBytes(avoidCopying = true))

            sendMessageToEnclave(outgoingMessage)
        }

        fun sendCallMessage(callType: EnclaveCallType, parameterBuffer: ByteBuffer?) {
            requireNotNull(parameterBuffer)
            sendMessage(callType.toByte(), CallInterfaceMessageType.CALL.toByte(), parameterBuffer)
        }

        fun sendReturnMessage(callType: HostCallType, returnBytes: ByteBuffer?) {
            sendMessage(callType.toByte(), CallInterfaceMessageType.RETURN.toByte(), returnBytes)
        }

        fun sendExceptionMessage(callType: HostCallType, exceptionBuffer: ByteBuffer?) {
            requireNotNull(exceptionBuffer)
            sendMessage(callType.toByte(), CallInterfaceMessageType.EXCEPTION.toByte(), exceptionBuffer)
        }

        fun handleCallMessage(callMessage: StreamCallInterfaceMessage) {
            val messageType = CallInterfaceMessageType.fromByte(callMessage.messageTypeID)
            require(messageType == CallInterfaceMessageType.CALL)

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
                when (CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)) {
                    CallInterfaceMessageType.CALL -> handleCallMessage(replyMessage)
                    else -> break
                }
            }

            val replyMessageType = CallInterfaceMessageType.fromByte(replyMessage.messageTypeID)
            val replyPayload = replyMessage.payload

            check(EnclaveCallType.fromByte(replyMessage.callTypeID) == callType)
            check(replyMessageType != CallInterfaceMessageType.CALL)

            if (replyMessageType == CallInterfaceMessageType.EXCEPTION) {
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
            synchronized(callGuardSemaphore) {
                check(isRunning) { "Call interface is not running." }
                callGuardSemaphore.acquire()
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
