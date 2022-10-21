package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.mail.internal.readInt
import com.r3.conclave.mail.internal.writeInt
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * This class is a streaming socket based implementation of the [HostEnclaveInterface].
 * It has three jobs:
 *  - Serve as the endpoint for calls to make to the enclave, see [com.r3.conclave.common.internal.CallInterface]
 *  - Route calls from the enclave to the appropriate host side call handler, see [com.r3.conclave.common.internal.CallInterface]
 *  - Handle the low-level details of the messaging protocol (socket with streamed ECalls and OCalls).
 */
class SocketHostEnclaveInterface(port: Int = 0) : HostEnclaveInterface(), Closeable {
    private val serverSocket = ServerSocket(port, 100)

    val port get() = serverSocket.localPort

    private lateinit var socketPool: ArrayBlockingQueue<Socket>

    /** Represents the lifecycle of the interface. */
    sealed class State {
        object Ready : State()
        object Running : State()
        object Stopped : State()
    }

    private val stateManager = StateManager<State>(State.Ready)

    val isRunning get() = (stateManager.state == State.Running)

    private var maxConcurrentCalls = 0
    private val callGuardSemaphore = Semaphore(0)

    /** Start the message receive loop thread and allow calls to begin. */
    fun start() {
        synchronized(stateManager) {
            if (stateManager.state == State.Running) return
            stateManager.transitionStateFrom<State.Ready>(to = State.Running) {
                "Interface may not be started multiple times."
            }

            try {
                /** Wait for IO sockets, then close the server socket. */
                serverSocket.use {
                    it.accept().use { initialSocket ->
                        maxConcurrentCalls = initialSocket.getInputStream().readInt()
                    }
                    socketPool = ArrayBlockingQueue(maxConcurrentCalls + 1)
                    for (i in 0 until maxConcurrentCalls) {
                        val socket = it.accept().apply { tcpNoDelay = true }
                        socketPool.put(socket)
                    }
                }

                /** Start the message receive thread and allow calls to enter. */
                callGuardSemaphore.release(maxConcurrentCalls)
            } catch (e: Exception) {
                stateManager.transitionStateFrom<State.Ready>(to = State.Stopped)
                throw e
            }
        }
    }

    /** Shut down both the host and the enclave call interface. */
    override fun close() {
        /**
         * Prevent new calls from entering the interface. This has to be synchronized to avoid race condition between
         * the semaphore acquisition and the lockout check in [executeOutgoingCall].
         */
        synchronized(stateManager) {
            if (stateManager.state == State.Stopped) return
            stateManager.transitionStateFrom<State.Running>(to = State.Stopped) {
                "Call interface is not running."
            }

            /** Wait for any pending calls to finish, this is released when a call context is retired. */
            callGuardSemaphore.acquireUninterruptibly(maxConcurrentCalls)

            /** Close all the sockets in the socket pool. */
            for (i in 0 until maxConcurrentCalls) {
                socketPool.take().use {
                    DataOutputStream(it.getOutputStream()).writeIntLengthPrefixBytes(
                            SocketCallInterfaceMessage.STOP_MESSAGE.toByteArray())
                }
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
            synchronized(stateManager) {
                stateManager.checkStateIs<State.Running> { "Call interface is not running." }
                callGuardSemaphore.acquireUninterruptibly()
            }
        }

        val enclaveCallContext = enclaveCallContexts.computeIfAbsent(threadID) {
            EnclaveCallContext(socketPool.take())
        }

        return try {
            enclaveCallContext.initiateCall(callType, parameterBuffer)
        } finally {
            if (!enclaveCallContext.hasActiveCalls()) {
                socketPool.put(enclaveCallContext.socket)
                enclaveCallContexts.remove(threadID)
                callGuardSemaphore.release()
            }
        }
    }
}
