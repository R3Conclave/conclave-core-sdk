package com.r3.conclave.enclave

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.common.internal.*
import com.r3.conclave.enclave.Enclave.State.*
import com.r3.sgx.core.common.*
import com.r3.sgx.core.common.crypto.internal.SignatureSchemeEdDSA
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.Enclavelet
import com.r3.sgx.core.enclave.RootEnclave
import net.i2p.crypto.eddsa.EdDSAEngine
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.Signature
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
    private companion object {
        private val signatureScheme = SignatureSchemeEdDSA()
    }

    internal val rootEnclave: RootEnclave = ConclaveRootEnclave()
    // TODO Persistence
    private val signingKeyPair = signatureScheme.generateKeyPair()
    private val stateManager = StateManager<State>(New)
    private lateinit var sender: Sender

    /**
     * Returns a [Signature] object pre-initialised with the private key corresponding
     * to the [signatureKey], ready for creation of digital signatures over
     * data you provide. The private key is not directly exposed to avoid accidental
     * mis-use (e.g. for encryption).
     */
    // TODO Tests
    protected fun createSignature(): Signature {
        val signature = Signature.getInstance(EdDSAEngine.SIGNATURE_ALGORITHM, SignatureSchemeEdDSA.securityProvider)
        signature.initSign(signingKeyPair.private)
        return signature
    }

    /** The public key used to sign data structures when [createSignature] is used. */
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
        val newReceiveState = ReceiveFromHost(callback)
        val previousReceiveState = stateManager.transitionStateFrom<ReceiveFromHost>(to = newReceiveState)
        sendToHost(bytes, isEnclaveCallReturn = false)
        return if (stateManager.state == newReceiveState) {
            // If the state hasn't changed then it means the host didn't have a response
            stateManager.state = previousReceiveState
            null
        } else {
            val response = stateManager.transitionStateFrom<HostResponse>(to = previousReceiveState)
            response.bytes
        }
    }

    private fun initialise(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) {
        val reportData = createReportData()
        sender = mux.addDownstream(EnclaveHandler())
        mux.addDownstream(Enclavelet.EnclaveletEpidAttestationEnclaveHandler(api, reportData))
    }

    private fun createReportData(): Cursor<ByteBuffer, SgxReportData> {
        // TODO
        return Cursor.allocate(SgxReportData)
    }

    private fun onReceive(input: ByteBuffer) {
        when (val state = stateManager.state) {
            New -> {
                input.checkNoRemaining()
                sender.send(1, Consumer { buffer ->
                    buffer.putBoolean(this is EnclaveCall)
                })
                stateManager.state = if (this is EnclaveCall) ReceiveFromHost(this) else HostReceiveNotSupported
            }
            is ReceiveFromHost -> {
                val isEnclaveCallReturn = input.getBoolean()
                val bytes = input.getRemainingBytes()
                if (isEnclaveCallReturn) {
                    stateManager.state = HostResponse(bytes)
                } else {
                    requireNotNull(state.callback) {
                        "Enclave has not provided a callback to callUntrustedHost to receive the host's call back in."
                    }
                    val response = state.callback.invoke(bytes)
                    if (response != null) {
                        sendToHost(response, isEnclaveCallReturn = true)
                    }
                }
            }
            else -> throw IllegalStateException(state.toString())
        }
    }

    /**
     * Pass the given bytes to the host, who will receive them synchronously. [isEnclaveCallReturn] tells the host whether
     * these bytes are the return value of an [EnclaveCall.invoke] (in which case it has to return itself) or are from
     * [callUntrustedHost] (in which case they need to be passed to the callback).
     */
    private fun sendToHost(bytes: ByteArray, isEnclaveCallReturn: Boolean) {
        sender.send(bytes.size + 1, Consumer { buffer ->
            buffer.putBoolean(isEnclaveCallReturn)
            buffer.put(bytes)
        })
    }

    private inner class ConclaveRootEnclave : RootEnclave() {
        override fun initialize(api: EnclaveApi, mux: SimpleMuxingHandler.Connection) = initialise(api, mux)
    }

    private inner class EnclaveHandler : Handler<Sender> {
        override fun connect(upstream: Sender): Sender = upstream
        override fun onReceive(connection: Sender, input: ByteBuffer) = onReceive(input)
    }

    private sealed class State {
        object New : State()
        object HostReceiveNotSupported : State()
        class ReceiveFromHost(val callback: EnclaveCall?) : State()
        class HostResponse(val bytes: ByteArray) : State()
    }
}

/**
 * Sends the given bytes to the registered [EnclaveCall] implementation provided to [EnclaveHost.callEnclave].
 * If the host responds by doing another call back in to the enclave rather than immediately returning
 * from the [EnclaveCall], that call will be routed to [callback]. In this way a form of virtual stack can
 * be built up between host and enclave as they call back and forth.
 *
 * @return The bytes returned from the host's [EnclaveCall].
 */
fun Enclave.callUntrustedHost(bytes: ByteArray, callback: (ByteArray) -> ByteArray?): ByteArray? {
    return callUntrustedHost(bytes, EnclaveCall { callback(it) })
}
