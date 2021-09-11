package com.r3.conclave.enclave

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.InternalCallType.*
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.handler.*
import com.r3.conclave.enclave.Enclave.CallState.Receive
import com.r3.conclave.enclave.Enclave.CallState.Response
import com.r3.conclave.enclave.internal.*
import com.r3.conclave.mail.*
import com.r3.conclave.mail.internal.MailDecryptingStream
import com.r3.conclave.utilities.internal.*
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.*
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
 *
 * Please use the method [onStartup] to provide the configuration required to initialise the enclave, and
 * the method [onShutdown] to release the resources held by the enclave before its destruction.
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
    private val enclaveStateManager = StateManager<EnclaveState>(EnclaveState.New)

    // The signing key pair are assigned with the same value retrieved from getDefaultKey.
    // Such key should always be the same if the enclave is running within the same CPU and having the same MRSIGNER.
    private lateinit var signingKeyPair: KeyPair
    private lateinit var adminHandler: AdminHandler
    private lateinit var attestationHandler: AttestationEnclaveHandler
    private lateinit var enclaveMessageHandler: EnclaveMessageHandler

    private val postOffices = HashMap<DestinationAndTopic, EnclavePostOffice>()

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
     * If this property is false (the default) then a lock will be taken and the enclave will process mail and calls
     * from the host serially. To build a multi-threaded enclave you must firstly, obviously, write thread safe code
     * so the untrusted host cannot cause malicious data corruption by causing race conditions inside the enclave, and
     * then override this method to make it return true. By doing so you signal that you're taking responsibility
     * for your own thread safety.
     */
    protected open val threadSafe: Boolean get() = false

    private fun receiveFromUntrustedHostInternal(bytes: ByteArray) : ByteArray? {
        check(enclaveStateManager.state !is EnclaveState.New) { "Communication between the host and the enclave is not possible. Enclave has not been started." }
        check(enclaveStateManager.state !is EnclaveState.Closed) { "Communication between the host and the enclave is not possible. Enclave has been closed." }
        return receiveFromUntrustedHost(bytes)
    }

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
     * Initialise an enclave class using a Mock environment.
     */
    @Suppress("unused")  // Accessed via reflection
    @PotentialPackagePrivate
    private fun initialiseMock(upstream: Sender, mockConfiguration: MockConfiguration?): HandlerConnected<*> {
        return initialise(MockEnclaveEnvironment(this, mockConfiguration), upstream)
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
        val reportBody = env.createReport(null, null)[body]
        val cpuSvn: ByteBuffer = reportBody[SgxReportBody.cpuSvn].read()
        val isvSvn: Int = reportBody[SgxReportBody.isvSvn].read()
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
    private class AdminHandler(
        private val enclave: Enclave,
        private val env: EnclaveEnvironment
    ) : Handler<AdminHandler> {
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
            when (val type = input.get().toInt()) {
                0 -> onAttestation(input)
                1 -> onOpen(input)
                2 -> onClose()
                else -> throw IllegalStateException("Unknown type $type")
            }
        }

        private fun onAttestation(input: ByteBuffer) {
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

        @Synchronized
        fun onOpen(input: ByteBuffer) {
            if (enclave.enclaveStateManager.state is EnclaveState.Started) return
            val ackReceipt = input.getNullable { getRemainingBytes() }
            if (ackReceipt != null) {
                enclave.enclaveMessageHandler.populateAcknowledgedRanges(ackReceipt)
            }
            enclave.onStartup()
            enclave.enclaveStateManager.transitionStateFrom<EnclaveState.New>(to = EnclaveState.Started)
        }

        @Synchronized
        private fun onClose() {
           if (enclave.enclaveStateManager.state is EnclaveState.Closed) return
           // This method call must be at the top so the enclave derived class can release its resources
           enclave.onShutdown()
           enclave.enclaveStateManager.transitionStateFrom<EnclaveState.Started>(to = EnclaveState.Closed)
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
        val enclaveInstanceInfo: EnclaveInstanceInfoImpl
            get() {
                if (_enclaveInstanceInfo == null) {
                    sendAttestationRequest()
                }
                return _enclaveInstanceInfo!!
            }

        /**
         * Send a request to the host for the [Attestation] object. The enclave has the other properties needed
         * to construct its [EnclaveInstanceInfoImpl]. This way less bytes are transferred and there's less checking that
         * needs to be done.
         */
        private fun sendAttestationRequest() {
            sender.send(1) { buffer ->
                buffer.put(1)
            }
        }
    }

    /**
     * Override this method if the enclave requires extra configuration. Please be aware that any initialization code
     * must be placed in this method and not in the class constructor to ensure the enclave is started correctly.
     * This method is invoked as the last step of the enclave initialization.
     */
    open fun onStartup() {

    }

    /**
     * Override this method to release any resources held by the enclave. This method is called when the enclave is
     * asked to be shut down. This gives an opportunity to the enclave to release its resources before being destroyed.
     */
    open fun onShutdown() {

    }


    private inner class EnclaveMessageHandler : Handler<EnclaveMessageHandler> {
        private val currentEnclaveCall = ThreadLocal<Long>()
        private val enclaveCalls = ConcurrentHashMap<Long, StateManager<CallState>>()

        /**
         * Mail acknowledgement and expected sequence number(s).
         *
         * - mail messages get a sequence number assigned by the sender's PostOffice.
         * - mail acknowledgement receipt (MAC) contains acknowledged sequence numbers (per topic, as ranges (to save the memory))
         *   - it gets updated everytime an enclave calls `acknowledge()` and emitted as [MailCommand.AcknowledgementReceipt].
         *
         * - in absence of MAC:
         *   - mail sequence numbers are expected to start from zero
         *   - and increment by 1 for every *new* message.
         *   - on receiving end those sequence numbers are expected to form a contiguous sequence.
         *
         * - conclave does not require you to acknowledge received mail
         * - nor does it impose any restrictions on order in which mail has to be acknowledged or which mail has to be acknowledged.
         *
         * - if all mail had been acknowledged:
         *   - the receipt will contain a single range (again, per topic) - {start:0, count:N},
         *     where N is number of messages acknowledged for this topic.
         *   - the next mail received is expected to have a sequence number N (start+count)
         *
         * - there are two collections tracking mail sequence numbers at the code below: `materialisedAckReceipt` and `nextExpectedSequences`
         *   - they look identical and, in fact, contain very same data (cloned) at the start
         *   - they are separate because: you still under no obligation to acknowledge received mail,
         *     so the received mail sequence number might (will) naturally be ahead of those acknowledged.
         *   - `materialisedAckReceipt` get updated on `acknowledge`,
         *      whereas `nextExpectedSequences` get updated at `checkMailOrdering` for incoming mail.
         *
         * Here is how it works (using a single topic example):
         * - initially, there is no MAC, so sequence numbers start with zero
         * - let's send three mails (seqnums: 0,1,2), but only acknowledge one with seq# 1
         * - the MAC received and kept by the host will have acknowledged range `(start:1,count:1)`
         * - let's now restart an enclave, but this time using MAC
         *   - `materialisedAckReceipt` = `[(start:1,count:1)]`
         *   - `nextExpectedSequences` = `[(start:1,count:1)]`
         *   - that is we are definitely missing seq# 0,
         *     on restart we have to replay all missing mail (seq# 0),
         *     the mail with seq#2 is at the tail and won't be distinguishable from new mail with same seq#.
         * - let's now replay the mail with seq# 2
         *   - it will be rejected as we expected seq# is 0
         *   - materialisedAckReceipt and nextExpectedSequences are staying intact
         * - let's now replay the mail with seq# 0
         *   - it will be accepted and `nextExpectedSequences` will now be `[(start:0,count:2)]`
         *   - if we now acknowledge this mail (seq# 0), the `materialisedAckReceipt` will also be `[(start:0,count:2)]`
         * - we can now safely send mail with seq#2
         *   - it will be accepted, nextExpectedSequences` will now be `[(start:0,count:3)]`
         *   - materialisedAckReceipt are still intact
         *   - if we now acknowledge this mail (seq# 2), the `materialisedAckReceipt` will also be `[(start:0,count:3)]`
         *
         */
        private val lockObject = Object()
        private var mustSendReceipt = false
        // this map keeps track of acknowledged mail (note: we keep sequence numbers (ranges), not mail ids)
        private val materialisedAckReceipt = HashMap<DestinationAndTopic,RangeSequence>()
        // this map says what sequence number to expect next
        private val nextExpectedSequences = HashMap<DestinationAndTopic,RangeSequence>()
        // this map shares instances of RangeSequence (via TargetRangeSequence) with materialisedAckReceipt
        private val mailIdToTargetRangeSequence = HashMap<Long, TargetRangeSequence>()

        private lateinit var sender: Sender

        override fun connect(upstream: Sender): EnclaveMessageHandler {
            sender = upstream
            return this
        }

        // .values() returns a fresh array each time so cache it here.
        private val callTypeValues = InternalCallType.values()

        // Variable so we can compare it in an assertion later.
        private val receiveFromUntrustedHostCallback = HostCallback(::receiveFromUntrustedHostInternal)

        // This method can be called concurrently by the host.
        override fun onReceive(connection: EnclaveMessageHandler, input: ByteBuffer) {
            val type = callTypeValues[input.get().toInt()]
            val hostThreadId = input.getLong()
            // Assign the host thread ID to the current thread so that callUntrustedHost/postMail/etc can pick up the
            // right state for the thread.
            currentEnclaveCall.set(hostThreadId)
            val stateManager = enclaveCalls.computeIfAbsent(hostThreadId) {
                // The initial state is to receive on receiveFromUntrustedHost.
                StateManager(Receive(receiveFromUntrustedHostCallback, receiveFromUntrustedHost = true))
            }
            when (type) {
                MAIL -> onMail(input)
                UNTRUSTED_HOST -> onUntrustedHost(stateManager, hostThreadId, input)
                CALL_RETURN -> onCallReturn(stateManager, input)
            }
        }

        private fun onMail(input: ByteBuffer) {
            val id = input.getLong()
            val routingHint = input.getNullable { String(getIntLengthPrefixBytes()) }
            // Wrap the remaining bytes in a InputStream to avoid copying.
            val decryptingStream = MailDecryptingStream(input.inputStream())
            val mail: EnclaveMail = decryptingStream.decryptMail { keyDerivation ->
                requireNotNull(keyDerivation) {
                    "Missing metadata to decrypt mail. Mail destined for an enclave must be created using a PostOffice " +
                            "from the enclave's EnclaveInstanceInfo object (i.e. EnclaveInstanceInfo.createPostOffice()). " +
                            "If the sender of this mail is another enclave then Enclave.postOffice(EnclaveInstanceInfo) " +
                            "must be used instead."
                }
                // Ignore any extra bytes in the keyDerivation.
                require(keyDerivation.size >= SgxCpuSvn.size + SgxIsvSvn.size) { "Invalid key derivation header size" }
                val keyDerivationBuffer = ByteBuffer.wrap(keyDerivation)
                val cpuSvn = keyDerivationBuffer.getSlice(SgxCpuSvn.size)
                val isvSvn = keyDerivationBuffer.getUnsignedShort()
                val entropy = getSecretEntropy(cpuSvn, isvSvn)
                // We now have the private key to decrypt the mail body and authenticate the header.
                Curve25519PrivateKey(entropy)
            }
            checkMailOrdering(id, mail)
            // We do locking for the user by default, because otherwise it'd be easy to forget that the host can
            // enter on multiple threads even if you aren't prepared for it. Spotting missing thread safety would
            // require spotting the absence of something rather than the presence of something, which is hard.
            // This works even if the host calls back into the enclave on the same stack. However if the host
            // makes a call on a separate thread, it's treated as a separate call as you'd expect.
            if (!threadSafe) {
                synchronized(this@Enclave) { this@Enclave.receiveMailInternal(id, mail, routingHint) }
            } else {
                this@Enclave.receiveMailInternal(id, mail, routingHint)
            }
            /**
             * We only need to emit a single receipt if multiple mail were acknowledged in this receiveMail call,
             * hence why we do it here and not in acknowledgeMail
             */
            emitAcknowledgementReceipt()
        }

        private fun onUntrustedHost(stateManager: StateManager<CallState>, hostThreadID: Long, input: ByteBuffer) {
            val state = stateManager.checkStateIs<Receive>()
            checkNotNull(state.callback) {
                "The enclave has not provided a callback to callUntrustedHost to receive the host's call back in."
            }
            // We do locking for the user by default, because otherwise it'd be easy to forget that the host can
            // enter on multiple threads even if you aren't prepared for it. Spotting missing thread safety would
            // require spotting the absence of something rather than the presence of something, which is hard.
            // This works even if the host calls back into the enclave on the same stack. However if the host
            // makes a call on a separate thread, it's treated as a separate call as you'd expect.
            val response = if (!threadSafe) {
                // If this is a recursive call, we already hold the lock and the synchronized statement is a no-op.
                // This assertion is here to document that fact.
                if (state.callback != receiveFromUntrustedHostCallback) check(Thread.holdsLock(this@Enclave))
                synchronized(this@Enclave) { state.callback.apply(input.getRemainingBytes()) }
            } else {
                state.callback.apply(input.getRemainingBytes())
            }
            if (response != null) {
                // If the user calls back into the host whilst handling a local call or a mail, they end up
                // inside callUntrustedHost. So, for a non-thread safe enclave it's OK that this is outside the
                // lock, because it'll be held whilst calling out to the enclave during an operation which is when
                // there's actual risk of corruption. By the time we get here the enclave should be done and ready
                // for the next request.
                sendToHost(CALL_RETURN, hostThreadID, response.size) { buffer ->
                    buffer.put(response)
                }
            }
        }

        private fun onCallReturn(stateManager: StateManager<CallState>, input: ByteBuffer) {
            stateManager.state = Response(input.getRemainingBytes())
        }

        private fun checkMailOrdering(mailID: Long, mail: EnclaveMail) {
            synchronized(lockObject) {
                val key = DestinationAndTopic(mail.authenticatedSender, mail.topic)

                // link mailID with acknowledged ranges
                mailIdToTargetRangeSequence[mailID] = TargetRangeSequence(
                    materialisedAckReceipt.computeIfAbsent(key) { RangeSequence() },
                    mail.sequenceNumber
                )

                val range = nextExpectedSequences.computeIfAbsent(key) { RangeSequence() }
                val expected = range.expected()
                val actual = mail.sequenceNumber
                check(actual == expected) {
                    when {
                        range.isEmpty() -> {
                            "First time seeing mail with topic ${mail.topic} so the sequence number must be zero but is " +
                                "instead ${mail.sequenceNumber}. It may be the host is delivering mail out of order."
                        }
                        actual < expected -> {
                            "Mail with sequence number ${mail.sequenceNumber} on topic ${mail.topic} has already been seen, " +
                                    "was expecting $expected. Make sure the same PostOffice instance is used for the same " +
                                    "sender key and topic, or if the sender key is long-term then a per-process topic is used. " +
                                    "Otherwise it may be the host is replaying older messages."
                        }
                        else -> {
                            "Next sequence number on topic ${mail.topic} should be $expected but is instead " +
                                    "${actual}. It may be the host is delivering mail out of order."
                        }
                    }
                }
                range.add(mail.sequenceNumber)
            }
        }

        fun callUntrustedHost(bytes: ByteArray, callback: HostCallback?): ByteArray? {
            val hostThreadID = checkNotNull(currentEnclaveCall.get()) {
                "Thread ${Thread.currentThread()} may not attempt to call out to the host outside the context of a call."
            }
            val stateManager = enclaveCalls.getValue(hostThreadID)
            val newReceiveState = Receive(callback, receiveFromUntrustedHost = false)
            // We don't expect the enclave to be in the Response state here as that implies a bug since Response is only
            // a temporary holder to capture the return value.
            // We take note of the current Receive state (i.e. the current callback) so that once this callUntrustedHost
            // has finished we revert back to it. This allows nested callUntrustedHost each with potentially their own
            // callback.
            val previousReceiveState = stateManager.transitionStateFrom<Receive>(to = newReceiveState)
            var response: Response? = null
            try {
                // This could re-enter the enclave in onReceive, if the user has provided a callback.
                sendToHost(UNTRUSTED_HOST, hostThreadID, bytes.size) { buffer ->
                    buffer.put(bytes)
                }
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
         * Pass the custom [payload] to the host, who will receive them synchronously.
         *
         * @param type Tells the host whether these bytes are the return value of a callback
         * (in which case it has to return itself) or are from [callUntrustedHost] (in which case they need to be passed
         * to the callback).
         * @param hostThreadId The thread ID received from the host which is sent back as is so that the host can know
         * which of the possible many concurrent calls this response is for.
         */
        private fun sendToHost(
            type: InternalCallType,
            hostThreadId: Long,
            payloadSize: Int,
            payload: (ByteBuffer) -> Unit
        ) {
            sender.send(1 + Long.SIZE_BYTES + payloadSize) { buffer ->
                buffer.put(type.ordinal.toByte())
                buffer.putLong(hostThreadId)
                payload(buffer)
            }
        }

        fun postMail(encryptedBytes: ByteArray, routingHint: String?) {
            val routingHintBytes = routingHint?.toByteArray()
            val size = nullableSize(routingHintBytes) { it.intLengthPrefixSize } + encryptedBytes.size
            sendMailCommandToHost(MailCommandType.POST, size) { buffer ->
                buffer.putNullable(routingHintBytes) { putIntLengthPrefixBytes(it) }
                buffer.put(encryptedBytes)
            }
        }

        fun acknowledgeMail(mailID: Long) {
            synchronized(lockObject) {
                updateAcknowledgementReceipt(mailID)
                sendMailCommandToHost(MailCommandType.ACKNOWLEDGE, Long.SIZE_BYTES) { buffer ->
                    buffer.putLong(mailID)
                }
            }
        }

        private fun updateAcknowledgementReceipt(mailID: Long) {
            val target = mailIdToTargetRangeSequence.remove(mailID)
            requireNotNull(target){ "Trying to acknowledge mail with unknown ID $mailID, or mail has already been acknowledged." }
            target.range.add(target.sequenceNumber)
            mustSendReceipt = true
        }

        /**
         * This data must not be seen unsealed outside of the enclave.
         *
         * Receipt format:
         * (0) version: Int
         * (1) number_of_topics: Int
         * for each topic:
         *   (2) public_key: 32 bytes
         *   (3) topic: 2 + UTF(text)
         *   (5) number_of_ranges: Int
         *   for each range:
         *      (6) start seq# (Long)
         *      (7) count (Long)
         */
        private fun emitAcknowledgementReceipt() {
            synchronized(lockObject) {
                if (!mustSendReceipt)
                    return

                val plainText = writeData {
                    writeInt(AcknowledgementReceiptVersion.value)
                    writeInt(materialisedAckReceipt.size)
                    materialisedAckReceipt.forEach { (key, ranges) ->
                        writeIntLengthPrefixBytes(key.destination.encoded)
                        writeUTF(key.topic)
                        ranges.write(this)
                    }
                }

                val sealed = env.sealData(PlaintextAndEnvelope(OpaqueBytes(plainText)))
                sendMailCommandToHost(MailCommandType.RECEIPT, sealed.size) { buffer ->
                    buffer.put(sealed)
                }

                mustSendReceipt = false
            }
        }

        fun populateAcknowledgedRanges(ackReceipt: ByteArray) {
            synchronized(lockObject) {
                val bis = env.unsealData(ackReceipt).plaintext.inputStream()
                val dis = DataInputStream(bis)

                val version = dis.readInt()
                check(version == AcknowledgementReceiptVersion.value) {
                    "Receipt version($version) does not match to expected(${AcknowledgementReceiptVersion.value})"
                }
                val numberOfTopics = dis.readInt() // number of key+topic
                repeat(numberOfTopics) {
                    val pk = Curve25519PublicKey(dis.readIntLengthPrefixBytes())
                    val topic = dis.readUTF()
                    val key = DestinationAndTopic(pk, topic)

                    val ranges = RangeSequence()
                    ranges.read(dis)
                    materialisedAckReceipt[key] = ranges
                    nextExpectedSequences[key] = ranges.clone()
                }
            }
        }

        private fun sendMailCommandToHost(mailType: MailCommandType, size: Int, block: (ByteBuffer) -> Unit) {
            val hostThreadId = checkNotNull(currentEnclaveCall.get()) {
                "Thread ${Thread.currentThread()} may not attempt to send or acknowledge mail outside the context of a call or delivery."
            }
            sendToHost(MAIL, hostThreadId, 1 + size) { buffer ->
                buffer.put(mailType.ordinal.toByte())
                block(buffer)
            }
        }
    }

    private sealed class CallState {
        class Receive(
            val callback: HostCallback?,
            @Suppress("unused") val receiveFromUntrustedHost: Boolean
        ) : CallState()

        class Response(val bytes: ByteArray) : CallState()
    }

    //region Mail
    private lateinit var encryptionKeyPair: KeyPair

    private fun receiveMailInternal(id: Long, mail: EnclaveMail, routingHint: String?) {
        check(enclaveStateManager.state !is EnclaveState.New) { "Enclave cannot receive mails. Enclave has not been started." }
        check(enclaveStateManager.state !is EnclaveState.Closed) { "Enclave cannot receive mails. Enclave has been closed." }
        receiveMail(id, mail, routingHint)
    }

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
     * @param mail Access to the decrypted/authenticated mail body+envelope.
     * @param routingHint An optional string provided by the host that can be passed to [postMail] to tell the
     * host that you wish to reply to whoever provided it with this mail (e.g. connection ID). Note that this may
     * not be the same as the logical sender of the mail if advanced anonymity techniques are being used, like
     * users passing mail around between themselves before it's delivered.
     */
    protected open fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
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
     * Returns a post office for mail targeted at the given destination key, and having the given topic. The post office
     * is setup with the enclave's private encryption key so the receipient can be sure mail originated from this enclave.
     *
     * The enclave will cache post offices so that the same instance is used for the same public key and topic. This
     * ensures mail is sequenced correctly.
     *
     * The recipient should use [PostOffice.decryptMail] to make sure it verifies this enclave as the sender of the mail.
     *
     * If the destination is an enclave then use the overload which takes in an [EnclaveInstanceInfo] instead.
     *
     * @see <a href="https://docs.conclave.net/mail.html#using-mail-for-storage">Using mail for storage</a>
     */
    protected fun postOffice(destinationPublicKey: PublicKey, topic: String): EnclavePostOffice {
        synchronized(postOffices) {
            return postOffices.computeIfAbsent(DestinationAndTopic(destinationPublicKey, topic)) {
                EnclavePostOfficeImpl(destinationPublicKey, topic, null)
            }
        }
    }

    /**
     * Returns a post office for mail targeted at the given destination key, and having the topic "default". The post office
     * is setup with the enclave's private encryption key so the receipient can be sure mail originated from this enclave.
     *
     * The enclave will cache post offices so that the same instance is used for the same public key and topic. This
     * ensures mail is sequenced correctly.
     *
     * The recipient should use [PostOffice.decryptMail] to make sure it verifies this enclave as the sender of the mail.
     *
     * If the destination is an enclave then use the overload which takes in an [EnclaveInstanceInfo] instead.
     *
     * @see <a href="https://docs.conclave.net/mail.html#using-mail-for-storage">Using mail for storage</a>
     */
    protected fun postOffice(destinationPublicKey: PublicKey): EnclavePostOffice {
        return postOffice(destinationPublicKey, "default")
    }

    /**
     * Returns a post office for responding back to the sender of the given mail. This is a convenience method which calls
     * `postOffice(PublicKey, String)` with the mail's authenticated sender key and topic.
     *
     * Note: Do not use this overload if the sender of the mail is another enclave. `postOffice(EnclaveInstanceInfo)` must
     * still be used when responding back to an enclave. This may mean having to ingest the sender's [EnclaveInstanceInfo]
     * object beforehand.
     *
     * @see <a href="https://docs.conclave.net/mail.html#using-mail-for-storage">Using mail for storage</a>
     */
    protected fun postOffice(mail: EnclaveMail): EnclavePostOffice = postOffice(mail.authenticatedSender, mail.topic)

    /**
     * Returns a post office for mail targeted to an enclave with the given topic. The target enclave can be one running
     * on this host or on another machine, and can even be this enclave if [enclaveInstanceInfo] is used (and thus enabling
     * the mail-to-self pattern). The post office is setup with the enclave's private encryption key so the receipient
     * can be sure mail originated from this enclave.
     *
     * The enclave will cache post offices so that the same instance is used for the same [EnclaveInstanceInfo] and topic.
     * This ensures mail is sequenced correctly.
     *
     * The recipient should use [PostOffice.decryptMail] to make sure it verifies this enclave as the sender of the mail.
     *
     * @see <a href="https://docs.conclave.net/mail.html#using-mail-for-storage">Using mail for storage</a>
     */
    protected fun postOffice(enclaveInstanceInfo: EnclaveInstanceInfo, topic: String): EnclavePostOffice {
        enclaveInstanceInfo as EnclaveInstanceInfoImpl
        synchronized(postOffices) {
            return postOffices.computeIfAbsent(DestinationAndTopic(enclaveInstanceInfo.encryptionKey, topic)) {
                EnclavePostOfficeImpl(enclaveInstanceInfo.encryptionKey, topic, enclaveInstanceInfo.keyDerivation)
            }
        }
    }

    /**
     * Returns a post office for mail targeted to an enclave with the topic "default". The target enclave can be one running
     * on this host or on another machine, and can even be this enclave if [enclaveInstanceInfo] is used (and thus enabling
     * the mail-to-self pattern). The post office is setup with the enclave's private encryption key so the receipient
     * can be sure mail did indeed originate from this enclave.
     *
     * The enclave will cache post offices so that the same instance is used for the same [EnclaveInstanceInfo] and topic.
     * This ensures mail is sequenced correctly.
     *
     * The recipient should use [PostOffice.decryptMail] to make sure it verifies this enclave as the sender of the mail.
     *
     * @see <a href="https://docs.conclave.net/mail.html#using-mail-for-storage">Using mail for storage</a>
     */
    protected fun postOffice(enclaveInstanceInfo: EnclaveInstanceInfo): EnclavePostOffice {
        return postOffice(enclaveInstanceInfo, "default")
    }

    /**
     * The provided mail will be encrypted, authenticated and passed to the host
     * for delivery.
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
    protected fun postMail(encryptedMail: ByteArray, routingHint: String?) {
        enclaveMessageHandler.postMail(encryptedMail, routingHint)
    }

    private inner class EnclavePostOfficeImpl(
        destinationPublicKey: PublicKey,
        topic: String,
        override val keyDerivation: ByteArray?
    ) : EnclavePostOffice(destinationPublicKey, topic) {
        init {
            minSizePolicy = defaultMinSizePolicy
        }

        override val senderPrivateKey: PrivateKey get() = encryptionKeyPair.private
    }

    // By default let all post office instances use the same moving average instance to make it harder to analyse mail
    // sizes within any given topic.
    private val defaultMinSizePolicy = MinSizePolicy.movingAverage()

    private data class DestinationAndTopic(val destination: PublicKey, val topic: String)
    private data class TargetRangeSequence(val range: RangeSequence, val sequenceNumber: Long)
    //endregion

    private sealed class EnclaveState {
        object New : EnclaveState()
        object Started : EnclaveState()
        object Closed : EnclaveState()
    }
}

// Typealias to make this code easier to read.
private typealias HostCallback = Function<ByteArray, ByteArray?>
