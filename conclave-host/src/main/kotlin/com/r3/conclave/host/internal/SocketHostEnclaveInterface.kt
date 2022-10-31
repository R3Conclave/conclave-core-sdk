package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.utilities.internal.getAllBytes
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore

/**
 * This class is a streaming socket based implementation of the [HostEnclaveInterface].
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (socket with streamed ECalls and OCalls).
 */
class SocketHostEnclaveInterface(private val maxConcurrentCalls: Int) : HostEnclaveInterface(), Closeable {
    private lateinit var serverSocket: ServerSocket

    private lateinit var callContextPool: ArrayBlockingQueue<EnclaveCallContext>

    /** Represents the lifecycle of the interface. */
    sealed class State {
        object Ready : State()
        object Running : State()
        object Stopped : State()
    }

    private val stateManager = StateManager<State>(State.Ready)

    val isRunning get() = synchronized(stateManager) { stateManager.state == State.Running }

    private val callGuardSemaphore = Semaphore(0)

    /**
     * Set up the server socket, binding the specified port.
     * If no specific port is requested, let the system allocate one.
     */
    fun bindPort(port: Int = 0): Int {
        serverSocket = ServerSocket(port)
        return serverSocket.localPort
    }

    /** Start the call interface and allow calls to begin. */
    fun start() {
        synchronized(stateManager) {
            if (stateManager.state == State.Running) return
            stateManager.transitionStateFrom<State.Ready>(to = State.Running) {
                "Interface may not be started multiple times."
            }

            try {
                /** Wait for IO sockets, then close the server socket. */
                serverSocket.use {

                    /** Send the maximum number of concurrent calls to the enclave. */
                    it.accept().use { initialSocket ->
                        DataOutputStream(initialSocket.getOutputStream()).writeInt(maxConcurrentCalls)
                    }

                    /**
                     * Set up a pool of re-usable call contexts, each with their own socket.
                     * TODO: Create connections lazily as they are required, rather than greedily at startup
                     */
                    callContextPool = ArrayBlockingQueue(maxConcurrentCalls + 1)
                    for (i in 0 until maxConcurrentCalls) {
                        val socket = it.accept().apply { tcpNoDelay = true }
                        val callContext = EnclaveCallContext(socket)
                        callContextPool.put(callContext)
                    }
                }

                /** Allow calls to enter the interface. */
                callGuardSemaphore.release(maxConcurrentCalls)
            } catch (e: Exception) {
                stateManager.transitionStateFrom<State.Ready>(to = State.Stopped)
                throw e
            }
        }
    }

    /**
     * Execute a graceful shutdown of both the host and enclave interfaces.
     * This will wait for any running calls to complete before returning.
     */
    override fun close() {
        synchronized(stateManager) {
            /** Prevent new calls from entering the interface. */
            if (stateManager.state == State.Stopped) return
            stateManager.transitionStateFrom<State.Running>(to = State.Stopped) {
                "Call interface is not running."
            }

            /** Wait for any pending calls to finish, this is released when a call completes. */
            callGuardSemaphore.acquireUninterruptibly(maxConcurrentCalls)

            /** Empty the call context pool and close all the call contexts. */
            for (i in 0 until maxConcurrentCalls) {
                callContextPool.take().close()
            }
        }
    }

    /**
     * Because of the way conclave currently works, a host call (enclave->host) never arrives outside the
     * context of a pre-existing enclave call (host->enclave).
     * This class represents that context and any recursive calls that take place within it.
     */
    private inner class EnclaveCallContext(val socket: Socket) {
        private var activeCalls = 0

        private val toEnclave = DataOutputStream(socket.getOutputStream())
        private val fromEnclave = DataInputStream(socket.getInputStream())

        fun hasActiveCalls(): Boolean = (activeCalls > 0)

        fun sendMessage(messageType: SocketCallInterfaceMessageType, callTypeID: Byte, payload: ByteBuffer?) {
            val message = SocketCallInterfaceMessage(messageType, callTypeID, payload?.getAllBytes(avoidCopying = true))
            toEnclave.writeIntLengthPrefixBytes(message.toByteArray())
        }

        fun receiveMessage(): SocketCallInterfaceMessage {
            val messageBytes = fromEnclave.readIntLengthPrefixBytes()
            return SocketCallInterfaceMessage.fromByteArray(messageBytes)
        }

        fun sendCallMessage(callType: EnclaveCallType, parameterBuffer: ByteBuffer) {
            sendMessage(SocketCallInterfaceMessageType.CALL, callType.toByte(), parameterBuffer)
        }

        fun sendReturnMessage(callType: HostCallType, returnBytes: ByteBuffer?) {
            sendMessage(SocketCallInterfaceMessageType.RETURN, callType.toByte(), returnBytes)
        }

        fun sendExceptionMessage(callType: HostCallType, exceptionBuffer: ByteBuffer) {
            sendMessage(SocketCallInterfaceMessageType.EXCEPTION, callType.toByte(), exceptionBuffer)
        }

        fun handleCallMessage(callMessage: SocketCallInterfaceMessage) {
            val messageType = callMessage.messageType
            require(messageType == SocketCallInterfaceMessageType.CALL)

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
            var replyMessage: SocketCallInterfaceMessage = receiveMessage()
            while (replyMessage.messageType == SocketCallInterfaceMessageType.CALL) {
                handleCallMessage(replyMessage)
                replyMessage = receiveMessage()
            }

            val replyMessageType = replyMessage.messageType
            val replyPayload = replyMessage.payload

            check(EnclaveCallType.fromByte(replyMessage.callTypeID) == callType)
            check(replyMessageType != SocketCallInterfaceMessageType.CALL)

            if (replyMessageType == SocketCallInterfaceMessageType.EXCEPTION) {
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

        /** Send a stop message to the enclave side worker thread corresponding to this context. */
        fun close() {
            check(!hasActiveCalls()) { "Cannot close a call interface with active calls." }
            toEnclave.writeIntLengthPrefixBytes(SocketCallInterfaceMessage.STOP_MESSAGE.toByteArray())
            socket.close()
        }
    }

    /**
     * Thread local enclave call contexts.
     * These are used to relate re-entering calls to an existing call context (if there is one).
     */
    private val threadLocalCallContext = ThreadLocal<EnclaveCallContext>()

    /**
     * Internal method for initiating an enclave call with specific arguments.
     * This should not be called directly, but instead by implementations in [HostEnclaveInterface].
     */
    override fun executeOutgoingCall(callType: EnclaveCallType, parameterBuffer: ByteBuffer): ByteBuffer? {
        synchronized(stateManager) {
            stateManager.checkStateIs<State.Running> { "Call interface is not running." }
        }

        val callContext = when(val existingCallContext = threadLocalCallContext.get()) {
            null -> {
                callGuardSemaphore.acquireUninterruptibly()
                val context = callContextPool.take()
                threadLocalCallContext.set(context)
                context
            }
            else -> existingCallContext
        }

        return try {
            callContext.initiateCall(callType, parameterBuffer)
        } finally {
            if (!callContext.hasActiveCalls()) {
                threadLocalCallContext.remove()
                callContextPool.put(callContext)        // Return to context pool.
                callGuardSemaphore.release()            // Free a slot for a new call to enter.
            }
        }
    }
}
