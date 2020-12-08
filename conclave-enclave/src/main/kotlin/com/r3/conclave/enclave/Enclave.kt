package com.r3.conclave.enclave

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.handler.*
import com.r3.conclave.enclave.Enclave.CallState.Receive
import com.r3.conclave.enclave.Enclave.CallState.Response
import com.r3.conclave.enclave.internal.AttestationEnclaveHandler
import com.r3.conclave.enclave.internal.EnclaveEnvironment
import com.r3.conclave.enclave.internal.InternalEnclave
import com.r3.conclave.mail.*
import com.r3.conclave.mail.internal.MailDecryptingStream
import com.r3.conclave.mail.internal.setKeyDerivation
import com.r3.conclave.utilities.internal.*
import java.nio.Buffer
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * Subclass this inside your enclave to provide an entry point. The outside world
 * communicates with the enclave via two mechanisms:
 *
 * 1. Local connections from the host. But remember the host is malicious in the SGX
 *    threat model, so anything received from the host cannot be completely trusted.
 *    Override and implement [receiveFromUntrustedHost] to receive the byte arrays sent via
 *    `EnclaveHost.callEnclave`.
 * 2. [EnclaveMail], an encrypted, authenticated and padded asynchronous messaging
 *    scheme. Clients that obtain a [EnclaveInstanceInfo] from the host can create
 *    mails and send it to the host for delivery. Override and implement [receiveMail] to receive mail via the host.
 *
 * Enclaves can sign things with a key that appears in the [com.r3.conclave.common.EnclaveInstanceInfo].
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

    private lateinit var env: EnclaveEnvironment

    // The signing key pair are assigned with the same value retrieved from getDefaultKey.
    // Such key should always be the same if the enclave is running within the same CPU and having the same MRSIGNER.
    private lateinit var signingKeyPair: KeyPair
    private lateinit var adminHandler: AdminHandler
    private lateinit var attestationHandler: AttestationEnclaveHandler
    private lateinit var enclaveMessageHandler: EnclaveMessageHandler

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

    /** The serializable remote attestation object for this enclave instance. */
    protected val enclaveInstanceInfo: EnclaveInstanceInfo get() = adminHandler.enclaveInstanceInfo

    /**
     * Override this method to receive bytes from the untrusted host via `EnclaveHost.callEnclave`.
     *
     * Default implementation throws [UnsupportedOperationException] so you should not perform a supercall.
     *
     * Any uncaught exceptions thrown by this method propagate to the calling `EnclaveHost.callEnclave`. In Java, checked
     * exceptions can be made to propagate by rethrowing them in an unchecked one.
     *
     * @param bytes Bytes received from the host.
     *
     * @return Bytes to sent back to the host as the return value of the `EnclaveHost.callEnclave` call. Can be null.
     */
    protected open fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray? {
        throw UnsupportedOperationException("This enclave does not support local host communication.")
    }

    /**
     * Sends the given bytes to the callback provided to `EnclaveHost.callEnclave`.
     *
     * @return The bytes returned from the host's callback.
     *
     * @throws IllegalStateException If no callback was provided to `EnclaveHost.callEnclave`.
     */
    protected fun callUntrustedHost(bytes: ByteArray): ByteArray? = callUntrustedHostInternal(bytes, null)

    /**
     * Sends the given bytes to the callback provided to `EnclaveHost.callEnclave`.
     * If the host responds by doing another call back into the enclave rather than immediately returning
     * from the callback, that call will be routed to [callback]. In this way a form of virtual stack can
     * be built up between host and enclave as they call back and forth.
     *
     * @return The bytes returned from the host's callback.
     *
     * @throws IllegalStateException If no callback was provided to `EnclaveHost.callEnclave`.
     */
    protected fun callUntrustedHost(bytes: ByteArray, callback: Function<ByteArray, ByteArray?>): ByteArray? {
        return callUntrustedHostInternal(bytes, callback)
    }

    private fun callUntrustedHostInternal(bytes: ByteArray, callback: HostCallback?): ByteArray? {
        return enclaveMessageHandler.callUntrustedHost(bytes, callback)
    }

    @Suppress("unused")  // Accessed via reflection
    @PotentialPackagePrivate
    private fun initialise(env: EnclaveEnvironment, upstream: Sender): HandlerConnected<*> {
        this.env = env
        // If the Enclave class implements InternalEnclave then the behaviour of the enclave is entirely delegated
        // to the InternalEnclave implementation and the Conclave-specific APIs (e.g. callUntrustedHost, etc) are
        // disabled. This allows us to test the enclave environment in scenarios where we don't want the Conclave handlers.
        return if (this is InternalEnclave) {
            this.internalInitialise(env, upstream)
        } else {
            initCryptography()
            val exposeErrors = env.enclaveMode != EnclaveMode.RELEASE
            val connected = HandlerConnected.connect(ExceptionSendingHandler(exposeErrors = exposeErrors), upstream)
            val mux = connected.connection.setDownstream(SimpleMuxingHandler())
            adminHandler = mux.addDownstream(AdminHandler(this, env))
            attestationHandler = mux.addDownstream(object : AttestationEnclaveHandler(env) {
                override val reportData = createReportData()
            })
            enclaveMessageHandler = mux.addDownstream(EnclaveMessageHandler())
            connected
        }
    }

    /**
     * Return 256 bits of stable entropy for the given CPUSVN + ISVNSVN. Even across enclave and host restarts the same
     * bytes will be returned for the same SVN values. The bytes are secret to the enclave and must not be leaked out.
     */
    private fun getSecretEntropy(cpuSvn: ByteBuffer, isvSvn: Int): ByteArray {
        // We get 128 bits of stable pseudo-randomness from the CPU, based on the enclave signer, per-CPU key and other
        // pieces of data.
        val secretKey = env.getSecretKey { keyRequest ->
            keyRequest[SgxKeyRequest.keyName] = KeyName.SEAL
            keyRequest[SgxKeyRequest.keyPolicy] = KeyPolicy.MRSIGNER
            keyRequest[SgxKeyRequest.cpuSvn] = cpuSvn
            keyRequest[SgxKeyRequest.isvSvn] = isvSvn
        }
        // For Curve25519 and EdDSA we need 256 bit keys. We hash it to convert it to 256 bits. This is safe because
        // the underlying 128 bits of entropy remains, and that's "safe" in the sense that nobody can brute force
        // 128 bits of entropy, not enough energy exists on Earth to make that feasible. Curve25519 needs 256 bits
        // for both private and public keys due to the existence of attacks on elliptic curve cryptography that
        // effectively halve the key size, so 256 bit keys -> 128 bits of work to brute force.
        return digest("SHA-256") { update(secretKey) }
    }

    private fun initCryptography() {
        val reportBody = env.createReport(null, null)[SgxReport.body]
        val cpuSvn: ByteBuffer = reportBody[SgxReportBody.cpuSvn].read()
        val isvSvn: Int = reportBody[SgxReportBody.isvSvn].read()
        keyDerivation = ByteBuffer.allocate(SgxCpuSvn.size + SgxIsvSvn.size).let {
            it.put(cpuSvn)
            it.putUnsignedShort(isvSvn)
            it.array()
        }
        (cpuSvn as Buffer).rewind()  // Prepare to read again.
        val entropy = getSecretEntropy(cpuSvn, isvSvn)
        signingKeyPair = signatureScheme.generateKeyPair(entropy)
        val private = Curve25519PrivateKey(entropy)
        encryptionKeyPair = KeyPair(private.publicKey, private)
    }

    private fun createReportData(): ByteCursor<SgxReportData> {
        val reportData = digest("SHA-512") {
            update(signatureKey.encoded)
            update(encryptionKeyPair.public.encoded)
        }
        return Cursor.wrap(SgxReportData, reportData)
    }

    /**
     * Handles the initial comms with the host - we send the host our info, it sends back an attestation response object
     * which we can use to build our [EnclaveInstanceInfo] to include in messages to other enclaves.
     */
    private class AdminHandler(private val enclave: Enclave, private val env: EnclaveEnvironment) : Handler<AdminHandler> {
        private lateinit var sender: Sender
        private var _enclaveInstanceInfo: EnclaveInstanceInfoImpl? = null

        override fun connect(upstream: Sender): AdminHandler {
            sender = upstream
            // At the time we send upstream the mux handler hasn't been configured for receiving, but that's OK.
            // The onReceive method will run later, when the AttestationResponse has been obtained from the attestation
            // servers.
            sendEnclaveInfo()
            return this
        }

        override fun onReceive(connection: AdminHandler, input: ByteBuffer) {
            val attestation = Attestation.get(input)
            val attestationReportBody = attestation.reportBody
            val enclaveReportBody = enclave.attestationHandler.report[body]
            check(attestationReportBody == enclaveReportBody) {
                """Host has provided attestation for a different enclave.
Expected: $enclaveReportBody
Received: $attestationReportBody"""
            }
            // It's also important to check the enclave modes match. Specifically we want to prevent an attestation marked
            // as secure from being used when the enclave is running in non-hardware mode (all non-hardware attestations
            // are insecure).
            check(attestation.enclaveMode == env.enclaveMode) {
                "The enclave mode of the attestation (${attestation.enclaveMode}) does not match ${env.enclaveMode}"
            }
            _enclaveInstanceInfo = EnclaveInstanceInfoImpl(
                    enclave.signatureKey,
                    enclave.encryptionKeyPair.public as Curve25519PublicKey,
                    attestation
            )
        }

        private fun sendEnclaveInfo() {
            val encodedSigningKey = enclave.signatureKey.encoded   // 44 bytes
            val encodedEncryptionKey = enclave.encryptionKeyPair.public.encoded   // 32 bytes
            sender.send(1 + encodedSigningKey.size + encodedEncryptionKey.size) { buffer ->
                buffer.put(0)  // Enclave info
                buffer.put(encodedSigningKey)
                buffer.put(encodedEncryptionKey)
            }
        }

        /**
         * Return the [EnclaveInstanceInfoImpl] for this enclave. The first time this is called it asks the host for the
         * [Attestation] object it received from the attestation service. From that the enclave is able to construct the
         * info object. By making this lazy we avoid slowing down the enclave startup process if it's never used.
         */
        @get:Synchronized
        val enclaveInstanceInfo: EnclaveInstanceInfoImpl get() {
            if (_enclaveInstanceInfo == null) {
                sendAttestationRequest()
            }
            return _enclaveInstanceInfo!!
        }

        /**
         * Send a request to the host for the [Attestation] object. The enclave has the other properties needed
         * to construct its [EnclaveInstanceInfoImpl].
         *
         * This is simpler than serialising the info object itself as then the enclave has to check it's the correct one.
         */
        private fun sendAttestationRequest() {
            sender.send(1) { buffer ->
                buffer.put(1)
            }
        }
    }

    private class Watermark(var value: Long)

    private inner class EnclaveMessageHandler : Handler<EnclaveMessageHandler> {
        private val currentEnclaveCall = ThreadLocal<Long>()
        private val enclaveCalls = ConcurrentHashMap<Long, StateManager<CallState>>()
        // Maps topics to the highest sequence number seen so far. Seqnos on a topic can start anywhere, but must
        // increment by one for each delivered mail. It's up to the app to check that a message starts with an
        // expected sequence number (e.g. zero) to avoid the host dropping initial messages.
        private val topicSequenceWatermarks = HashMap<String, Watermark>()
        private lateinit var sender: Sender

        override fun connect(upstream: Sender): EnclaveMessageHandler {
            sender = upstream
            return this
        }

        private val callTypeValues = InternalCallType.values()

        // This method can be called concurrently by the host.
        override fun onReceive(connection: EnclaveMessageHandler, input: ByteBuffer) {
            val threadID = input.getLong()
            val type = callTypeValues[input.get().toInt()]
            // Assign the host thread ID to the current thread so that callUntrustedHost/postMail/etc can pick up the
            // right state for the thread.
            currentEnclaveCall.set(threadID)
            val stateManager = enclaveCalls.computeIfAbsent(threadID) {
                // The initial state is to receive on receiveFromUntrustedHost.
                StateManager(Receive(::receiveFromUntrustedHost, receiveFromUntrustedHost = true))
            }
            if (type == InternalCallType.CALL_RETURN) {
                stateManager.state = Response(input.getRemainingBytes())
            } else if (type == InternalCallType.MAIL_DELIVERY) {
                val id = input.getLong()
                val routingHint = String(input.getIntLengthPrefixBytes()).takeIf { it.isNotEmpty() }
                // Wrap the remaining bytes in a InputStream to avoid copying.
                val decryptingStream = MailDecryptingStream(input.inputStream())
                val mail: EnclaveMail = decryptingStream.decryptMail { keyDerivation ->
                    requireNotNull(keyDerivation) { "Key derivation header required for decrypting enclave mail" }
                    // Ignore any extra bytes in the keyDerivation.
                    require(keyDerivation.size >= SgxCpuSvn.size + SgxIsvSvn.size) { "Invalid key derivation header size" }
                    val keyDerivationBuffer = ByteBuffer.wrap(keyDerivation)
                    val cpuSvn = keyDerivationBuffer.getSlice(SgxCpuSvn.size)
                    val isvSvn = keyDerivationBuffer.getUnsignedShort()
                    val entropy = getSecretEntropy(cpuSvn, isvSvn)
                    // We now have the private key to decrypt the mail body and authenticate the header.
                    Curve25519PrivateKey(entropy)
                }
                checkMailOrdering(mail)
                this@Enclave.receiveMail(id, routingHint, mail)
            } else {
                val state = stateManager.checkStateIs<Receive>()
                checkNotNull(state.callback) {
                    "The enclave has not provided a callback to callUntrustedHost to receive the host's call back in."
                }
                val response = state.callback.apply(input.getRemainingBytes())
                if (response != null) {
                    sendCallToHost(threadID, response, InternalCallType.CALL_RETURN)
                }
            }
        }

        private fun checkMailOrdering(mail: EnclaveMail) {
            synchronized(topicSequenceWatermarks) {
                var andReturn = false
                val highestSeen = topicSequenceWatermarks.computeIfAbsent(mail.topic) {
                    andReturn = true
                    Watermark(0)
                }
                if (andReturn) return
                if (mail.sequenceNumber != highestSeen.value + 1)
                    throw InvalidSequenceException(mail.sequenceNumber, highestSeen.value)
                highestSeen.value++
            }
        }

        fun callUntrustedHost(bytes: ByteArray, callback: HostCallback?): ByteArray? {
            val threadID = checkNotNull(currentEnclaveCall.get()) {
                "Thread ${Thread.currentThread()} may not attempt to call out to the host outside the context of a call."
            }
            val stateManager = enclaveCalls.getValue(threadID)
            val newReceiveState = Receive(callback, receiveFromUntrustedHost = false)
            // We don't expect the enclave to be in the Response state here as that implies a bug since Response is only
            // a temporary holder to capture the return value.
            // We take note of the current Receive state (i.e. the current callback) so that once this callUntrustedHost
            // has finished we revert back to it. This allows nested callUntrustedHost each with potentially their own
            // callback.
            val previousReceiveState = stateManager.transitionStateFrom<Receive>(to = newReceiveState)
            var response: Response? = null
            try {
                sendCallToHost(threadID, bytes, InternalCallType.CALL)
            } finally {
                // We revert the state even if an exception was thrown in the callback. This enables the user to have
                // their own exception handling and reuse of the host-enclave communication channel for another call.
                if (stateManager.state === newReceiveState) {
                    // If the state hasn't changed then it means the host didn't have a response
                    stateManager.state = previousReceiveState
                } else {
                    response = stateManager.transitionStateFrom(to = previousReceiveState)
                }
            }
            return response?.bytes
        }

        /**
         * Pass the given [bytes] to the host, who will receive them synchronously.
         *
         * @param threadID The thread ID received from the host which is sent back as is so that the host can know
         * which of the possible many concurrent calls this response is for.
         * @param type Tells the host whether these bytes are the return value of a callback
         * (in which case it has to return itself) or are from [callUntrustedHost] (in which case they need to be passed
         * to the callback).
         */
        private fun sendCallToHost(threadID: Long, bytes: ByteArray, type: InternalCallType) {
            sender.send(Long.SIZE_BYTES + 1 + bytes.size) { buffer ->
                buffer.putLong(threadID)
                buffer.put(type.ordinal.toByte())
                buffer.put(bytes)
            }
        }

        fun postMail(encryptedBytes: ByteArray, routingHint: String?) {
            val routingHintBytes = routingHint?.toByteArray()
            val size = Int.SIZE_BYTES + (routingHintBytes?.size ?: 0) + encryptedBytes.size
            sendMailCommandToHost(size, mailType = 0) { buffer ->
                if (routingHintBytes != null) {
                    buffer.putIntLengthPrefixBytes(routingHintBytes)
                } else {
                    buffer.putInt(0)
                }
                buffer.put(encryptedBytes)
            }
        }

        fun acknowledgeMail(mailID: Long) {
            sendMailCommandToHost(Long.SIZE_BYTES, mailType = 1) { buffer ->
                buffer.putLong(mailID)
            }
        }

        private fun sendMailCommandToHost(size: Int, mailType: Byte, block: (ByteBuffer) -> Unit) {
            val threadID = checkNotNull(currentEnclaveCall.get()) {
                "Thread ${Thread.currentThread()} may not attempt to send or acknowledge mail outside the context of a call or delivery."
            }

            sender.send(Long.SIZE_BYTES + 2 + size) { buffer ->
                buffer.putLong(threadID)
                buffer.put(InternalCallType.MAIL_DELIVERY.ordinal.toByte())
                buffer.put(mailType)
                block(buffer)
            }
        }
    }

    private sealed class CallState {
        class Receive(val callback: HostCallback?, @Suppress("unused") val receiveFromUntrustedHost: Boolean) : CallState()
        class Response(val bytes: ByteArray) : CallState()
    }

    //region Mail
    private lateinit var encryptionKeyPair: KeyPair
    private lateinit var keyDerivation: ByteArray

    /**
     * Invoked when a mail has been delivered by the host (via `EnclaveHost.deliverMail`), successfully decrypted
     * and authenticated (so the [EnclaveMail.authenticatedSender] property is reliable).
     *
     * Default implementation throws [UnsupportedOperationException] so you should not
     * perform a supercall.
     *
     * Received mail should be acknowledged by passing it to [acknowledgeMail], as
     * otherwise it will be redelivered if the enclave restarts.
     *
     * By not acknowledging mail in a topic until a multi-step messaging conversation
     * is finished, you can ensure that the conversation survives restarts and
     * upgrades.
     *
     * Any uncaught exceptions thrown by this method propagate to the calling `EnclaveHost.deliverMail`. In Java, checked
     * exceptions can be made to propagate by rethrowing them in an unchecked one.
     *
     * @param id An opaque identifier for the mail.
     * @param routingHint An optional string provided by the host that can be passed to [postMail] to tell the
     * host that you wish to reply to whoever provided it with this mail (e.g. connection ID). Note that this may
     * not be the same as the logical sender of the mail if advanced anonymity techniques are being used, like
     * users passing mail around between themselves before it's delivered.
     * @param mail Access to the decrypted/authenticated mail body+envelope.
     */
    protected open fun receiveMail(id: Long, routingHint: String?, mail: EnclaveMail) {
        throw UnsupportedOperationException("This enclave does not support receiving mail.")
    }

    /**
     * Informs the host that the mail should not be redelivered after the next
     * restart and can be safely deleted. Mail acknowledgements are atomic and
     * only take effect once the enclave returns control to the host, so, calling
     * this method multiple times on multiple different mails is safe even if the
     * enclave is interrupted part way through.
     */
    protected fun acknowledgeMail(mailID: Long) {
        enclaveMessageHandler.acknowledgeMail(mailID)
    }

    /**
     * Returns a new [MutableMail] object for the given recipient. It is initialized with the enclave's
     * private encryption key which means this enclave will be authenticated to them.
     *
     * You should set the [MutableMail.minSize] appropriately based on your knowledge
     * of the application's communication patterns. The outbound mail will not be
     * padded for you.
     *
     * The recipient should use [EnclaveInstanceInfo.decryptMail] to make sure it verifies this enclave as the sender
     * of the mail.
     */
    protected fun createMail(to: PublicKey, body: ByteArray): MutableMail {
        return MutableMail(body, to, encryptionKeyPair.private).apply {
            setKeyDerivation(keyDerivation)
        }
    }

    /**
     * The provided mail will be encrypted, authenticated and passed to the host
     * for delivery. Once the host accepts, the mail's sequence number is automatically
     * incremented, meaning you can immediately change the body and call [postMail]
     * again.
     *
     * Where the mail gets delivered depends on the host logic: in some
     * applications the public key may be sufficient, in others, the enclave may
     * need or want to provide additional direction using the [routingHint]
     * parameter.
     *
     * Note that posting and acknowledging mail is transactional. The delivery will
     * only actually take place once the current enclave call or [receiveMail] call
     * is finished. All posts and acknowledgements take place atomically, that is,
     * you can acknowledge a mail and post a reply, or the other way around, and it
     * doesn't matter which order you pick: you cannot get lost or replayed messages.
     */
    protected fun postMail(mail: MutableMail, routingHint: String?) {
        val encryptedBytes = mail.encrypt()
        // TODO: Track size of encryptedBytes here, and adjust minSize before calling encrypt to ensure uniform sizes.
        enclaveMessageHandler.postMail(encryptedBytes, routingHint)
        mail.incrementSequenceNumber()
    }
    //endregion
}

// Typealias to make this code easier to read.
private typealias HostCallback = Function<ByteArray, ByteArray?>
