package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.enclave.internal.EnclaveUtils.sanitiseThrowable
import com.r3.conclave.mail.internal.readInt
import com.r3.conclave.utilities.internal.getAllBytes
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * This class is the implementation of the [EnclaveHostInterface] for native enclaves.
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the host, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the host to the appropriate enclave side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (socket with streamed ECalls and OCalls).
 */
class SocketEnclaveHostInterface(
        private val host: String,
        private val port: Int
) : EnclaveHostInterface(), Closeable {
    var sanitiseExceptions = false

    /** Represents the lifecycle of the interface. */
    sealed class State {
        object Ready : State()
        object Running : State()
        object Stopped : State()
    }

    private var stateManager = StateManager<State>(State.Ready)

    private lateinit var callExecutor: ExecutorService

    private var maximumConcurrentCalls = 0

    fun start() {
        synchronized(stateManager) {
            if (stateManager.state == State.Running) return
            stateManager.transitionStateFrom<State.Ready>(to = State.Running) {
                "Call interface may not be started multiple times."
            }

            try {
                /** Receive the maximum number of concurrent calls from the host and set up the call executor service. */
                Socket(host, port).use { initialSocket ->
                    maximumConcurrentCalls = initialSocket.getInputStream().readInt()
                    callExecutor = Executors.newFixedThreadPool(maximumConcurrentCalls)
                }

                /** Connect sockets and instantiate handler threads. */
                for (i in 0 until maximumConcurrentCalls) {
                    val socket = Socket(host, port).apply { tcpNoDelay = true }
                    callExecutor.execute { EnclaveCallContext(socket).handlerLoop() }
                }
            } catch (e: Exception) {
                stateManager.transitionStateFrom<State.Running>(to = State.Stopped)
                throw e
            }
        }
    }

    /**
     * The shutdown process starts on the host.
     * This function just blocks until the host sends a stop message and the message receive loop terminates.
     */
    override fun close() {
        synchronized(stateManager) {
            if (stateManager.state == State.Stopped) return
            stateManager.transitionStateFrom<State.Running>(to = State.Stopped) {
                "Call interface is not running."
            }
            callExecutor.shutdown()
        }
    }

    fun awaitTermination() {
        var done = false
        while(!done) {
            try {
                done = callExecutor.awaitTermination(10, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                continue
            }
        }
    }

    private inner class EnclaveCallContext(private val socket: Socket) {
        private val toHost = DataOutputStream(socket.getOutputStream())
        private val fromHost = DataInputStream(socket.getInputStream())

        fun receiveMessage(): SocketCallInterfaceMessage {
            val messageBytes = fromHost.readIntLengthPrefixBytes()
            return SocketCallInterfaceMessage.fromByteArray(messageBytes)
        }

        fun sendMessage(messageType: SocketCallInterfaceMessageType, callTypeID: Byte, payload: ByteBuffer?) {
            val message = SocketCallInterfaceMessage(messageType, callTypeID, payload?.getAllBytes(avoidCopying = true))
            toHost.writeIntLengthPrefixBytes(message.toByteArray())
        }

        fun sendCallMessage(callType: HostCallType, parameterBuffer: ByteBuffer) {
            sendMessage(SocketCallInterfaceMessageType.CALL, callType.toByte(), parameterBuffer)
        }

        fun sendReturnMessage(callType: EnclaveCallType, returnBytes: ByteBuffer?) {
            sendMessage(SocketCallInterfaceMessageType.RETURN, callType.toByte(), returnBytes)
        }

        fun sendExceptionMessage(callType: EnclaveCallType, exceptionBuffer: ByteBuffer) {
            sendMessage(SocketCallInterfaceMessageType.EXCEPTION, callType.toByte(), exceptionBuffer)
        }

        fun handleCallMessage(callMessage: SocketCallInterfaceMessage) {
            val messageType = callMessage.messageType
            require(messageType == SocketCallInterfaceMessageType.CALL)

            val callType = EnclaveCallType.fromByte(callMessage.callTypeID)
            val parameterBytes = checkNotNull(callMessage.payload) { "Received call message without parameter bytes." }

            val returnBuffer = try {
                handleIncomingCall(callType, ByteBuffer.wrap(parameterBytes))
            } catch (t: Throwable) {
                val maybeSanitisedThrowable = if (sanitiseExceptions) sanitiseThrowable(t) else t
                sendExceptionMessage(callType, ByteBuffer.wrap(ThrowableSerialisation.serialise(maybeSanitisedThrowable)))
                return
            }

            sendReturnMessage(callType, returnBuffer)
        }

        fun initiateCall(callType: HostCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
            sendCallMessage(callType, parameterBuffer)

            /** Iterate, handling CALL messages until a message that is not a CALL arrives */
            var replyMessage = receiveMessage()
            while (replyMessage.messageType == SocketCallInterfaceMessageType.CALL) {
                handleCallMessage(replyMessage)
                replyMessage = receiveMessage()
            }

            val replyMessageType = replyMessage.messageType
            val replyPayload = replyMessage.payload

            check(HostCallType.fromByte(replyMessage.callTypeID) == callType)
            check(replyMessageType != SocketCallInterfaceMessageType.CALL)

            if (replyMessageType == SocketCallInterfaceMessageType.EXCEPTION) {
                checkNotNull(replyPayload) { "Received exception message without parameter bytes." }
                throw ThrowableSerialisation.deserialise(replyPayload)
            }

            return replyPayload?.let { ByteBuffer.wrap(replyPayload) }
        }

        /**
         * Handle an initial call.
         * "Initial" calls are calls that are received by the main loop below.
         */
        fun handleInitialCall(message: SocketCallInterfaceMessage) {
            check(message.messageType == SocketCallInterfaceMessageType.CALL)
            check(threadLocalCallContext.get() == null)
            try {
                threadLocalCallContext.set(this)
                handleCallMessage(message)
            } finally {
                threadLocalCallContext.remove()
            }
        }

        /** Handle calls from the host until told by the host to stop. */
        fun handlerLoop() {
            socket.use {
                var message = receiveMessage()
                while (message.messageType != SocketCallInterfaceMessageType.STOP) {
                    handleInitialCall(message)
                    message = receiveMessage()
                }
            }
        }
    }

    /**
     * This contains the call context for the current thread.
     * It is used to link outgoing calls to the appropriate call worker.
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
