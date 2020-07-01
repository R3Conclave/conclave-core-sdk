package com.r3.conclave.enclave

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.*
import com.r3.conclave.enclave.Enclave.EnclaveCallHandler.State.Receive
import com.r3.conclave.enclave.Enclave.EnclaveCallHandler.State.Response
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import com.r3.conclave.enclave.internal.EpidAttestationEnclaveHandler
import com.r3.conclave.enclave.internal.InternalEnclave
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * Subclass this inside your enclave to provide an entry point. The outside world
 * communicates with the enclave via two mechanisms:
 *
 * 1. Local connections from the host. But remember the host is malicious in the SGX
 *    threat model, so anything received from the host cannot be completely trusted.
 *    Implement [EnclaveCall] to receive the byte arrays sent via
 *    [EnclaveHost.callEnclave].
 * 2. [EnclaveMail], an encrypted, authenticated and padded asynchronous messaging
 *    scheme. Clients that obtain a [EnclaveInstanceInfo] from the host can create
 *    mails and send it to the host for delivery.
 *
 * Enclaves can sign things with a key that appears in the [EnclaveInstanceInfo].
 * This can be useful when the enclave is being used to create a proof of correct
 * computation, rather than operate on secret data.
 */
abstract class Enclave {
    /**
     * Suppress kotlin specific companion objects from our API documentation.
     * @suppress
     */
    private companion object {
        private val signatureScheme = SignatureSchemeEdDSA()
    }

    // The signing key pair are assigned with the same value retrieved from getDefaultKey.
    // Such key should always be the same if the enclave is running within the same CPU and having the same MRSIGNER.
    private lateinit var signingKeyPair: KeyPair
    // This is only initialised if the enclave implements EnclaveCall
    private var enclaveCallHandler: EnclaveCallHandler? = null

    /**
     * Returns a [Signature] object pre-initialised with the private key corresponding
     * to the [signatureKey], ready for creation of digital signatures over
     * data you provide. The private key is not directly exposed to avoid accidental
     * mis-use (e.g. for encryption).
     */
    protected fun signer(): Signature {
        val signature = SignatureSchemeEdDSA.createSignature()
        signature.initSign(signingKeyPair.private)
        return signature
    }

    /** The public key used to sign data structures when [signer] is used. */
    protected val signatureKey: PublicKey get() = signingKeyPair.public

    /**
     * Sends the given bytes to the registered [EnclaveCall] implementation provided to [EnclaveHost.callEnclave].
     *
     * @return The bytes returned from the host's [EnclaveCall].
     */
    fun callUntrustedHost(bytes: ByteArray): ByteArray? = callUntrustedHostInternal(bytes, null)

    /**
     * Sends the given bytes to the registered [EnclaveCall] implementation provided to [EnclaveHost.callEnclave].
     * If the host responds by doing another call back in to the enclave rather than immediately returning
     * from the [EnclaveCall], that call will be routed to [callback]. In this way a form of virtual stack can
     * be built up between host and enclave as they call back and forth.
     *
     * @return The bytes returned from the host's [EnclaveCall].
     */
    fun callUntrustedHost(bytes: ByteArray, callback: EnclaveCall): ByteArray? = callUntrustedHostInternal(bytes, callback)

    private fun callUntrustedHostInternal(bytes: ByteArray, callback: EnclaveCall?): ByteArray? {
        val enclaveCallHandler = checkNotNull(this.enclaveCallHandler) {
            "Enclave needs to implement EnclaveCall to receive and send to the host."
        }
        return enclaveCallHandler.callUntrustedHost(bytes, callback)
    }

    @Suppress("unused")  // Accessed via reflection
    @PotentialPackagePrivate
    private fun initialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
        // If the Enclave class implements InternalEnclave then the behaviour of the enclave is entirely delegated
        // to the InternalEnclave implementation and the Conclave-specific APIs (e.g. callUntrustedHost, etc) are
        // disabled. This allows us to test the enclave environment in scenerios where we don't want the Conclave handlers.
        return if (this is InternalEnclave) {
            this.internalInitialise(env, upstream)
        } else {
            signingKeyPair = signatureScheme.generateKeyPair(env.defaultSealingKey())
            val exposeErrors = env.enclaveMode != EnclaveMode.RELEASE
            val connected = HandlerConnected.connect(ExceptionSendingHandler(exposeErrors = exposeErrors), upstream)
            val mux = connected.connection.setDownstream(SimpleMuxingHandler())
            mux.addDownstream(AdminHandler(this))
            mux.addDownstream(object : EpidAttestationEnclaveHandler(env) {
                override val reportData = createReportData()
            })
            if (this is EnclaveCall) {
                enclaveCallHandler = mux.addDownstream(EnclaveCallHandler(this))
            }
            connected
        }
    }

    private fun createReportData(): ByteCursor<SgxReportData> {
        val sha512 = MessageDigest.getInstance("SHA-512")
        sha512.update(signatureKey.encoded)
        // TODO Include the encryption key in the hash
        return Cursor(SgxReportData, sha512.digest())
    }

    private class AdminHandler(private val enclave: Enclave) : Handler<AdminHandler> {
        override fun connect(upstream: Sender): AdminHandler {
            // We can send the enclave info message in the connect method since we're not expecting a response back.
            sendEnclaveInfo(upstream)
            return this
        }

        override fun onReceive(connection: AdminHandler, input: ByteBuffer) {
            throw IllegalStateException("Not expecting a response on this channel")
        }

        private fun sendEnclaveInfo(sender: Sender) {
            val encodedKey = enclave.signatureKey.encoded
            sender.send(1 + encodedKey.size, Consumer { buffer ->
                buffer.putBoolean(enclave is EnclaveCall)
                buffer.put(encodedKey)
            })
        }
    }

    private class EnclaveCallHandler(private val enclave: EnclaveCall) : Handler<EnclaveCallHandler> {
        private val currentEnclaveCall = ThreadLocal<Long>()
        private val enclaveCalls = ConcurrentHashMap<Long, StateManager<State>>()
        private lateinit var sender: Sender

        override fun connect(upstream: Sender): EnclaveCallHandler {
            sender = upstream
            return this
        }

        // This method can be called concurrently by the host.
        override fun onReceive(connection: EnclaveCallHandler, input: ByteBuffer) {
            val enclaveCallId = input.getLong()
            val isEnclaveCallReturn = input.getBoolean()
            val bytes = input.getRemainingBytes()
            // Assign the call ID to the current thread so that callUntrustedHost can pick up the right state for the thread.
            currentEnclaveCall.set(enclaveCallId)
            val stateManager = enclaveCalls.computeIfAbsent(enclaveCallId) {
                // The initial state is to receive on the Enclave's EnclaveCall implementation.
                StateManager(Receive(enclave))
            }
            val state = stateManager.checkStateIs<Receive>()
            if (isEnclaveCallReturn) {
                stateManager.state = Response(bytes)
            } else {
                requireNotNull(state.callback) {
                    "The enclave has not provided a callback to callUntrustedHost to receive the host's call back in."
                }
                val response = state.callback.invoke(bytes)
                if (response != null) {
                    sendToHost(enclaveCallId, response, isEnclaveCallReturn = true)
                }
            }
        }

        fun callUntrustedHost(bytes: ByteArray, callback: EnclaveCall?): ByteArray? {
            val enclaveCallId = checkNotNull(currentEnclaveCall.get()) {
                "Thread ${Thread.currentThread()} has not been given the call ID"
            }
            val stateManager = enclaveCalls.getValue(enclaveCallId)
            val newReceiveState = Receive(callback)
            // We expect the state to be ReceiveFromHost
            val previousReceiveState = stateManager.transitionStateFrom<Receive>(to = newReceiveState)
            sendToHost(enclaveCallId, bytes, isEnclaveCallReturn = false)
            return if (stateManager.state === newReceiveState) {
                // If the state hasn't changed then it means the host didn't have a response
                stateManager.state = previousReceiveState
                null
            } else {
                val response = stateManager.transitionStateFrom<Response>(to = previousReceiveState)
                response.bytes
            }
        }

        /**
         * Pass the given [bytes] to the host, who will receive them synchronously.
         *
         * @param enclaveCallId The call ID received from the host which is sent back as is so that the host can know
         * which of the possible many concurrent calls this response is for.
         *
         * @param isEnclaveCallReturn Tells the host whether these bytes are the return value of an [EnclaveCall.invoke]
         * (in which case it has to return itself) or are from [callUntrustedHost] (in which case they need to be passed
         * to the callback).
         */
        private fun sendToHost(enclaveCallId: Long, bytes: ByteArray, isEnclaveCallReturn: Boolean) {
            sender.send(Long.SIZE_BYTES + 1 + bytes.size, Consumer { buffer ->
                buffer.putLong(enclaveCallId)
                buffer.putBoolean(isEnclaveCallReturn)
                buffer.put(bytes)
            })
        }

        private sealed class State {
            class Receive(val callback: EnclaveCall?) : State()
            class Response(val bytes: ByteArray) : State()
        }
    }
}
