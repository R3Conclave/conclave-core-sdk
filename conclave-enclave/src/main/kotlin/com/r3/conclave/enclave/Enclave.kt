package com.r3.conclave.enclave

import com.r3.conclave.common.*
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.InternalCallType.*
import com.r3.conclave.common.internal.SgxReport.body
import com.r3.conclave.common.internal.SgxReportBody.isvProdId
import com.r3.conclave.common.internal.SgxReportBody.mrenclave
import com.r3.conclave.common.internal.SgxReportBody.mrsigner
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.handler.*
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.enclave.Enclave.CallState.Receive
import com.r3.conclave.enclave.Enclave.CallState.Response
import com.r3.conclave.enclave.Enclave.EnclaveState.*
import com.r3.conclave.enclave.internal.*
import com.r3.conclave.enclave.internal.kds.KdsPrivateKeyResponse
import com.r3.conclave.mail.*
import com.r3.conclave.mail.internal.DecryptedEnclaveMail
import com.r3.conclave.mail.internal.EnclaveStateId
import com.r3.conclave.mail.internal.MailDecryptingStream
import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.mail.internal.readEnclaveStateId
import com.r3.conclave.utilities.internal.*
import java.io.UTFDataFormatException
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function
import kotlin.concurrent.withLock

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
 * Enclaves can sign things with a key that appears in the [EnclaveInstanceInfo].
 * This can be useful when the enclave is being used to create a proof of correct
 * computation, rather than operate on secret data.
 *
 * Please use the method [onStartup] to provide the configuration required to initialise the enclave, and
 * the method [onShutdown] to release the resources held by the enclave before its destruction.
 *
 * The enclave also provides a persistent key-value store ([persistentMap]) which is resistant to rollback attacks by
 * the host. Use this to persist data that needs to exist across enclave restarts. Any data stored just in local
 * variables will be reset on restart. The default file system can also be used both as a temporary scratchpad but also
 * for persistence.
 */
abstract class Enclave {
    private companion object {
        private val signatureScheme = SignatureSchemeEdDSA()
        // The constant key name used for the KDS persistence key. This value cannot change without breaking the
        // enclave's ability to decrypt previously sealed data.
        private const val KDS_PERSISTENCE_KEY_NAME = "EnclavePersistence"

        private fun getMailDecryptingStream(input: ByteBuffer): MailDecryptingStream {
            // Wrap the remaining bytes in a InputStream to avoid copying.
            return MailDecryptingStream(input.inputStream())
        }
    }

    private var kdsEiiForPersistence: EnclaveInstanceInfo? = null
    private lateinit var env: EnclaveEnvironment
    // The signing key pair are assigned with the same value retrieved from getDefaultKey.
    // Such key should always be the same if the enclave is running within the same CPU and having the same MRSIGNER.
    private lateinit var signingKeyPair: KeyPair
    private lateinit var adminHandler: AdminHandler
    private lateinit var attestationHandler: AttestationEnclaveHandler
    private lateinit var enclaveMessageHandler: EnclaveMessageHandler
    private var persistenceKdsPrivateKey: ByteArray? = null

    private val lastSeenStateIds = HashMap<PublicKey, EnclaveStateId>()
    private val postOffices = HashMap<PublicKeyAndTopic, SessionEnclavePostOffice>()
    private val lock = ReentrantLock()
    private val enclaveQuiescentCondition = lock.newCondition()

    /**
     * Guarded by [lock]
     */
    private val enclaveStateManager = StateManager<EnclaveState>(New)

    /**
     * Guarded by [lock]
     */
    private var numberReceiveCallsExecuting = 0

    private val _persistentMap = LinkedHashMap<String, ByteArray>()

    /**
     * Returns a persistent key-value store where string keys can be mapped to byte values. Use this [MutableMap] to
     * securely store data that needs to be available across enclave restarts.
     *
     * The entire map is serialised and encrypted after each [receiveMail] and [receiveFromUntrustedHost] call and is
     * given to the host to persist. On restart the host is required to use the latest version of the map to
     * re-initialise the enclave. Conclave makes a best-effort attempt at preventing the host from being able to rewind
     * map, i.e. using an older version instead of the latest.
     *
     * The persistent map is not enabled by default. This is done by setting the
     * [`enablePersistentMap`](https://docs.conclave.net/enclave-configuration.html#enablepersistentmap-maxpersistentmapsize)
     * configuration in the enclave's build.gradle to true. The map is also not available if the enclave is multi-threaded
     * (i.e. [threadSafe] is overridden to return `true`). Support for this maybe added in a future version.
     *
     * Note, the keys are encoded using UTF-8 and the encoded size of each key cannot be more than 65535 bytes.
     *
     * @throws IllegalStateException If the persistent map is not enabled.
     */
    val persistentMap: MutableMap<String, ByteArray> get() {
        check(env.enablePersistentMap) {
            "The enclave persistent map is not enabled. To enable the persistent map for your enclave, add " +
            "\"def enablePersistentMap = true\" to your enclave build.gradle. For more information on the persistent " +
            "map and the consequences of enabling it, consult the Conclave documentation."
        }
        return _persistentMap
    }

    /**
     * Returns a [Signature] object pre-initialised with the private key corresponding
     * to the [signatureKey], ready for creation of digital signatures over
     * data you provide. The private key is not directly exposed to avoid accidental
     * mis-use (e.g. for encryption).
     *
     * When serialising your signed data structure, consider also attaching the serialised [EnclaveInstanceInfo] of this
     * enclave with it ([enclaveInstanceInfo]). This ensures the signature can still be verified by the end-user even
     * if the enclave's signing key changes.
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
     * The remote attestation object for the KDS enclave this enclave is using for persistence. This will be null if
     * this enclave is not configured to use a KDS.
     */
    protected val kdsEnclaveInstanceInfo: EnclaveInstanceInfo? get() = kdsEiiForPersistence

    /**
     * If this property is false (the default) then a lock will be taken and the enclave will process mail and calls
     * from the host serially. To build a multi-threaded enclave you must firstly, obviously, write thread safe code
     * so the untrusted host cannot cause malicious data corruption by causing race conditions inside the enclave, and
     * then override this method to make it return true. By doing so you signal that you're taking responsibility
     * for your own thread safety.
     */
    protected open val threadSafe: Boolean get() = false

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
            /*
            Here we should not add any code that throws exceptions. This is because the handler mechanisms haven't
            yet been put in place and an exception would cause the Enclave to terminate in such a way that we can't
            control and without meaningful stacktrace as this can't be reported back.
            */
            val exceptionSendingHandler = ExceptionSendingHandler(env.enclaveMode == EnclaveMode.RELEASE)
            val connected = HandlerConnected.connect(exceptionSendingHandler, upstream)
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
    private fun initialiseMock(
        upstream: Sender,
        mockConfiguration: MockConfiguration?,
        kdsConfig: EnclaveKdsConfig?
    ): HandlerConnected<*> {
        return initialise(MockEnclaveEnvironment(this, mockConfiguration, kdsConfig), upstream)
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

    private fun getLocalSecretKey(): ByteArray {
        val reportBody = env.createReport(null, null)[body]
        val cpuSvn: ByteBuffer = reportBody[SgxReportBody.cpuSvn].read()
        val isvSvn: Int = reportBody[SgxReportBody.isvSvn].read()

        return env.getSecretKey { keyRequest ->
            keyRequest[SgxKeyRequest.keyName] = KeyName.SEAL
            keyRequest[SgxKeyRequest.keyPolicy] = KeyPolicy.MRSIGNER
            keyRequest[SgxKeyRequest.cpuSvn] = cpuSvn
            keyRequest[SgxKeyRequest.isvSvn] = isvSvn
        }
    }

    private fun getPersistencePrivateKey(): ByteArray {
        val persistenceKeySpec = env.kdsConfiguration?.persistenceKeySpec

        if (persistenceKeySpec != null) {
            val persistenceKdsPrivateKey = persistenceKdsPrivateKey
            if (persistenceKdsPrivateKey != null) {
                // The KDS key may be longer than 128 bit, so we only use the first 128 bit
                return persistenceKdsPrivateKey.copyOf(16)
            }
            // TODO There's no exception message as these are not propageted in release mode. However, from this example
            //  and others we need a very special and privileged exception that will carry any necessary information
            //  that's needed even in release.

            // If the enclave has been configured to use a KDS and the host doesn't provide one, then for dev and
            // testing the enclave will default to a machine-specific MRSIGNER key. However this most probably isn't
            // what the enclave's clients would want to happen when connecting to a production release enclave.
            check(env.enclaveMode != EnclaveMode.RELEASE)
            if (env.enclaveMode == EnclaveMode.DEBUG) {
                println("WARN: The enclave has been configured to use a KDS for encrypting persistent data but the " +
                        "host has not provided one. Defaulting to a machine-only MRSIGNER key. However, this enclave " +
                        "will not start in release mode if there is no KDS.")
            }
        }
        return getLocalSecretKey()
    }

    private fun setupFileSystems() {
        val privateKey = getPersistencePrivateKey()
        val inMemorySize = env.inMemoryFileSystemSize
        val persistentSize = env.persistentFileSystemSize

        if (inMemorySize > 0L && persistentSize == 0L ||
            inMemorySize == 0L && persistentSize > 0L) {
            //  We do not allow other mount point apart from "/" when only one filesystem is present
            env.setupFileSystems(inMemorySize, persistentSize,"/", "/", privateKey)
        } else if (inMemorySize > 0L && persistentSize > 0L) {
            env.setupFileSystems(inMemorySize, persistentSize, "/tmp/", "/", privateKey)
        }
    }

    private fun initCryptography() {
        // We generate a random session key on enclave start and use it for the enclave's encryption and signing key.
        // This means they change each time the enclave restarts. This is perfectly acceptable as mail is (now) only
        // used for communication and not for longer-term persistence. If there are in-flight mail when the enclave
        // restarts they will be rejected and the client will need to be notified of that so that it can resubmit using
        // the new EnclaveInstanceInfo. In fact we rely on this property to prevent the host from replaying old mail to
        // the enclave on restart.
        val sessionKey = ByteArray(32).also(Noise::random)
        signingKeyPair = signatureScheme.generateKeyPair(sessionKey)
        val private = Curve25519PrivateKey(sessionKey)
        encryptionKeyPair = KeyPair(private.publicKey, private)
    }

    private fun createReportData(): ByteCursor<SgxReportData> {
        val reportData = digest("SHA-512") {
            update(signatureKey.encoded)
            update(encryptionKeyPair.public.encoded)
        }
        return Cursor.wrap(SgxReportData, reportData)
    }

    private fun applySealedState(sealedStateBlob: ByteArray) {
        if (!env.enablePersistentMap) return
        val sealedState = try {
            // Decrypt sealed state using KDS key when the Enclave has been configured to obtain one,
            // otherwise use the unsealing functions.
            if (env.kdsConfiguration != null) {
                decryptSealedState(sealedStateBlob).plaintext
            } else {
                env.unsealData(sealedStateBlob).plaintext
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error occurred while unsealing enclave state: ${e.message}", e)
        }
        sealedState.deserialise {
            val version = read()
            check(version == 1)
            readEnclaveStateId()  // This is not used currently but it's here in case it's needed later.
            // TODO Feed the time into native code https://r3-cev.atlassian.net/browse/CON-615
            run {
                val epochSecond = readLong()
                val nano = readInt()
                Instant.ofEpochSecond(epochSecond, nano.toLong())
            }
            repeat(readInt()) {
                val key = readUTF()
                val value = readIntLengthPrefixBytes()
                _persistentMap[key] = value
            }
            repeat(readInt()) {
                val clientPublicKey = Curve25519PublicKey(readExactlyNBytes(32))
                val lastSeenStateId = readEnclaveStateId()
                lastSeenStateIds[clientPublicKey] = lastSeenStateId
            }
        }
    }

    private fun decryptSealedState(sealedBlob: ByteArray): PlaintextAndEnvelope {
        return EnclaveUtils.aesDecrypt(getPersistencePrivateKey(), sealedBlob)
    }

    /**
     * Creates the private header that needs to be attached along side the user's body in the outbound mail. The header
     * contains the current sealed state ID and what the enclave thinks is the last ID the recipient has seen. This is
     * used to protect against state rewind by the host.
     */
    private fun getMailPrivateHeader(receiveContext: ReceiveContext, publicKey: PublicKey): ByteArray {
        receiveContext.outboundClients += publicKey

        return writeData {
            writeByte(1)  // Version
            write(receiveContext.stateId.bytes)
            nullableWrite(lastSeenStateIds[publicKey]) { write(it.bytes) }
        }
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
        private lateinit var persistenceKdsKeySpec: KDSKeySpec

        private val messageTypes = HostToEnclave.values()

        override fun connect(upstream: Sender): AdminHandler {
            sender = upstream
            // At the time we send upstream the mux handler hasn't been configured for receiving, but that's OK.
            // The onReceive method will run later, when the AttestationResponse has been obtained from the attestation
            // servers.
            sendEnclaveInfo()
            return this
        }

        override fun onReceive(connection: AdminHandler, input: ByteBuffer) {
            when (messageTypes[input.get().toInt()]) {
                HostToEnclave.ATTESTATION -> onAttestation(input)
                HostToEnclave.OPEN -> onOpen(input)
                HostToEnclave.CLOSE -> onClose()
                HostToEnclave.PERSISTENCE_KDS_KEY_SPEC_REQUEST -> onPersistenceKdsKeySpecRequest()
                HostToEnclave.PERSISTENCE_KDS_PRIVATE_KEY_RESPONSE -> onPersistenceKdsPrivateKeyResponse(input)
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

        private fun onOpen(input: ByteBuffer) {
            enclave.lock.withLock {
                /*
                Functions that are not strictly related to the enclave initialization but that needs to be executed
                  before the enclave starts up need to be placed here as here we can handle
                  and catch exceptions properly.
                Specifically, setupFileSystems can throw an exception in case the Host does not provide a
                filesystem file path for it and by having the call here allows us to handle this gracefully.
                 */
                check(!(env.enablePersistentMap && enclave.threadSafe)) {
                    "The persistent map is not available in multi-threaded enclaves."
                }

                enclave.enclaveStateManager.transitionStateFrom<New>(to = Started)
                val sealedStateBlob = input.getNullable { getRemainingBytes() }
                if (sealedStateBlob != null) {
                    enclave.applySealedState(sealedStateBlob)
                }
                enclave.setupFileSystems()
                enclave.onStartup()
            }
        }

        private fun onClose() {
            enclave.lock.withLock {
                enclave.enclaveStateManager.transitionStateFrom<Started>(to = Closed)
                // Wait until all receive calls being processed have completed
                while (enclave.numberReceiveCallsExecuting > 0) {
                    enclave.enclaveQuiescentCondition.await()
                }
                // This method call must be at the top so the enclave derived class can release its resources
                // Any code that releases resources used by the enclave must be placed after the function
                // onShutdown
                enclave.onShutdown()
            }
        }

        private fun sendEnclaveInfo() {
            val encodedSigningKey = enclave.signatureKey.encoded   // 44 bytes
            val encodedEncryptionKey = enclave.encryptionKeyPair.public.encoded   // 32 bytes
            val payloadSize = encodedSigningKey.size + encodedEncryptionKey.size
            sendToHost(EnclaveToHost.ENCLAVE_INFO, payloadSize) { buffer ->
                buffer.put(encodedSigningKey)
                buffer.put(encodedEncryptionKey)
            }
        }

        private fun onPersistenceKdsKeySpecRequest() {
            // The enclave is free to not use a KDS so it can ignore the key spec request if a kds config hasn't been
            // defined. The host will see that we've not sent back a key spec.
            val persistenceKeySpec = enclave.env.kdsConfiguration?.persistenceKeySpec ?: return
            persistenceKdsKeySpec = KDSKeySpec(
                KDS_PERSISTENCE_KEY_NAME,
                persistenceKeySpec.masterKeyType,
                buildPersistencePolicyConstraint(persistenceKeySpec)
            )
            sendKdsPersistenceKeySpecToHost(persistenceKdsKeySpec)
        }

        private fun buildPersistencePolicyConstraint(persistenceKeySpec: EnclaveKdsConfig.PersistenceKeySpec): String {
            val builder = StringBuilder(persistenceKeySpec.policyConstraint.constraint)

            val parsedUserContraint = EnclaveConstraint.parse(persistenceKeySpec.policyConstraint.constraint, false)

            val report = env.createReport(null, null)
            if (persistenceKeySpec.policyConstraint.useOwnCodeHash) {
                val mrenclave = SHA256Hash.get(report[body][mrenclave].read())
                if (mrenclave !in parsedUserContraint.acceptableCodeHashes) {
                    builder.append(" C:").append(mrenclave)
                }
            }

            if (persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID) {
                val mrsigner = SHA256Hash.get(report[body][mrsigner].read())
                val productId = report[body][isvProdId].read()
                if (mrsigner !in parsedUserContraint.acceptableSigners) {
                    builder.append(" S:").append(mrsigner)
                }
                if (parsedUserContraint.productID == null) {
                    builder.append(" PROD:").append(productId)
                } else {
                    require(parsedUserContraint.productID == productId) {
                        "Cannot apply useOwnCodeSignerAndProductID to the KDS persistence policy constraint as " +
                                "PROD:${parsedUserContraint.productID} is already specified"
                    }
                }
            }

            return builder.toString()
        }

        private fun onPersistenceKdsPrivateKeyResponse(input: ByteBuffer) {
            check(enclave.kdsEiiForPersistence == null) {
                "Enclave has already received a KDS persistence private key."
            }
            val kdsConfig = checkNotNull(env.kdsConfiguration) {
                "Host is attempting to send in a KDS persistence private key even though the enclave is not " +
                        "configured to use a KDS"
            }
            val privateKeyResponse = getKdsPrivateKeyResponse(input)
            enclave.kdsEiiForPersistence = privateKeyResponse.kdsEnclaveInstanceInfo
            enclave.persistenceKdsPrivateKey = privateKeyResponse.getPrivateKey(
                kdsConfig,
                expectedKeySpec = persistenceKdsKeySpec
            )
        }

        fun getKdsPrivateKeyResponse(input: ByteBuffer): KdsPrivateKeyResponse {
            val mailDecryptingStream = getMailDecryptingStream(input.getIntLengthPrefixSlice())
            val kdsResponseMail = mailDecryptingStream.decryptMail(enclave.encryptionKeyPair.private)
            // TODO Introduce an internal (or public API) deserialize method which takes in a ByteBuffer so that we
            //  can avoid the byte copying done here and in other places.
            val kdsEnclaveInstanceInfo = EnclaveInstanceInfo.deserialize(input.getIntLengthPrefixBytes())
            return KdsPrivateKeyResponse(kdsResponseMail, kdsEnclaveInstanceInfo)
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
            sendToHost(EnclaveToHost.ATTESTATION, 0) { }
        }

        fun sendKdsPersistenceKeySpecToHost(keySpec: KDSKeySpec) {
            val nameBytes = keySpec.name.toByteArray()
            val policyConstraintBytes = keySpec.policyConstraint.toByteArray()
            val payloadSize = nameBytes.intLengthPrefixSize + 1 + policyConstraintBytes.size
            sendToHost(EnclaveToHost.PERSISTENCE_KDS_KEY_SPEC_RESPONSE, payloadSize) { buffer ->
                buffer.putIntLengthPrefixBytes(nameBytes)
                buffer.put(keySpec.masterKeyType.ordinal.toByte())
                buffer.put(policyConstraintBytes)
            }
        }

        private fun sendToHost(type: EnclaveToHost, payloadSize: Int, payload: (ByteBuffer) -> Unit) {
            sender.send(1 + payloadSize) { buffer ->
                buffer.put(type.ordinal.toByte())
                payload(buffer)
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
        private val kdsPrivateKeyCache = ConcurrentHashMap<KDSKeySpec, PrivateKey>()
        private lateinit var sender: Sender

        private val currentEnclaveCall = ThreadLocal<Long>()
        private val enclaveCalls = ConcurrentHashMap<Long, StateManager<CallState>>()
        // Maps sender + topic pairs to the highest sequence number seen so far. Sequence numbers must start from zero
        // and can only increment by one for each delivered mail.
        private val sequenceWatermarks = HashMap<PublicKeyAndTopic, SequenceWatermark>()
        /**
         * Holds the current [ReceiveContext], or null if there isn't a receive* action being executed of if the thread
         * is multi-threaded, in which case this is always null.
         *
         * Note, if the current receive context was also needed to be tracked for multi-threaded enclaves then this
         * variable would be a [ThreadLocal].
         */
        var currentReceiveContext: ReceiveContext? = null

        override fun connect(upstream: Sender): EnclaveMessageHandler {
            sender = upstream
            return this
        }

        // .values() returns a fresh array each time so cache it here.
        private val callTypeValues = InternalCallType.values()

        // Variable so we can compare it in an assertion later.
        private val receiveFromUntrustedHostCallback = HostCallback(::receiveFromUntrustedHost)

        // This method can be called concurrently by the host.
        override fun onReceive(connection: EnclaveMessageHandler, input: ByteBuffer) {
            val type = callTypeValues[input.get().toInt()]
            val hostThreadId = input.long
            // Assign the host thread ID to the current thread so that callUntrustedHost/postMail/etc can pick up the
            // right state for the thread.
            currentEnclaveCall.set(hostThreadId)
            val stateManager = enclaveCalls.computeIfAbsent(hostThreadId) {
                // The initial state is to receive on receiveFromUntrustedHost.
                StateManager(Receive(receiveFromUntrustedHostCallback, receiveFromUntrustedHost = true))
            }
            when (type) {
                MAIL -> onMail(hostThreadId, input)
                UNTRUSTED_HOST -> onUntrustedHost(stateManager, hostThreadId, input)
                CALL_RETURN -> onCallReturn(stateManager, input)
                SEALED_STATE -> throw UnsupportedOperationException("SEALED_STATE is not expected from the host")
            }
        }

        // TODO Mail acks: https://r3-cev.atlassian.net/browse/CON-616
        private fun onMail(hostThreadId: Long, input: ByteBuffer) {
            val routingHint = input.getNullable { String(getIntLengthPrefixBytes()) }
            // This is the KDS private key response the host made on behalf of the enclave. The host is only required
            // to provide this if the enclave hasn't previously cached the private key this Mail needs. The host
            // determines this by examining the mail's unencrypted derivation header.
            val kdsPrivateKeyResponse = input.getNullable { adminHandler.getKdsPrivateKeyResponse(this) }
            val mailStream = getMailDecryptingStream(input)

            val keyDerivation = MailKeyDerivation.deserialiseFromMailStream(mailStream)
            val mail = when (keyDerivation) {
                RandomSessionKeyDerivation -> mailStream.decryptMail(encryptionKeyPair.private)
                is KdsKeySpecKeyDerivation -> {
                    mailStream.decryptKdsMail(getKdsPrivateKey(keyDerivation.keySpec, kdsPrivateKeyResponse))
                }
            }

            val preReceiveAction = if (keyDerivation is KdsKeySpecKeyDerivation) {
                // We don't check the sequence numbers for KDS encrypted mail because such a mail could be processed by
                // any number of enclave instances, example: horizontal scaling of an enclave application. In such a
                // scenario the first mail in the sequence might go to enclave 1 and the second mail to enclave 2.
                // Enclave 2 would then complain that the sequence number has not started from zero.
                { }
            } else {
                { checkMailOrdering(mail) }
            }
            executeReceive(hostThreadId, preReceiveAction) { receiveMail(mail, routingHint) }
        }

        private fun getKdsPrivateKey(keySpec: KDSKeySpec, kdsPrivateKeyResponse: KdsPrivateKeyResponse?): PrivateKey {
            var kdsPrivateKey = kdsPrivateKeyCache[keySpec]
            if (kdsPrivateKey != null) {
                return kdsPrivateKey
            }

            val kdsConfig = checkNotNull(env.kdsConfiguration) {
                "Enclave received a Mail which is encrypted using a KDS private key, but the enclave has not been" +
                        " configured to use a KDS."
            }
            checkNotNull(kdsPrivateKeyResponse) {
                "The mail is encrypted with a KDS private key but the host has not provided the KDS response."
            }

            val encodedPrivateKey = kdsPrivateKeyResponse.getPrivateKey(kdsConfig, expectedKeySpec = keySpec)
            kdsPrivateKey = Curve25519PrivateKey(encodedPrivateKey)

            // There's a race condition here if multiple threads deal with the same key spec. However it's not an
            // issue since the KDS response for all of them will be the same.
            kdsPrivateKeyCache[keySpec] = kdsPrivateKey
            return kdsPrivateKey
        }

        private fun onUntrustedHost(stateManager: StateManager<CallState>, hostThreadId: Long, input: ByteBuffer) {
            val state = stateManager.checkStateIs<Receive>()
            checkNotNull(state.callback) {
                "The enclave has not provided a callback to callUntrustedHost to receive the host's call back in."
            }

            val bytes = input.getRemainingBytes()

            val response = if (state.callback == receiveFromUntrustedHostCallback) {
                // Top-level, i.e. receiveFromUntrustedHost
                executeReceive(
                    hostThreadId,
                    preReceive = { },
                    receiveMethod = { receiveFromUntrustedHost(bytes) }
                )
            } else {
                // Recursive callback
                if (!threadSafe) {
                    // This check is to document that for a non thread-safe enclave the recursive call still holds the
                    // lock.
                    check(lock.isHeldByCurrentThread)
                }
                state.callback.apply(bytes)
            }

            if (response != null) {
                // If the user calls back into the host whilst handling a local call or a mail, they end up
                // inside callUntrustedHost. So, for a non-thread safe enclave it's OK that this is outside the
                // lock, because it'll be held whilst calling out to the enclave during an operation which is when
                // there's actual risk of corruption. By the time we get here the enclave should be done and ready
                // for the next request.
                sendToHost(CALL_RETURN, hostThreadId, response.size) { buffer ->
                    buffer.put(response)
                }
            }
        }

        private fun onCallReturn(stateManager: StateManager<CallState>, input: ByteBuffer) {
            stateManager.state = Response(input.getRemainingBytes())
        }

        private fun checkMailOrdering(mail: EnclaveMail) {
            val key = PublicKeyAndTopic(mail.authenticatedSender, mail.topic)
            val watermark = sequenceWatermarks.computeIfAbsent(key) { SequenceWatermark() }
            watermark.checkOrdering(mail)
        }

        private fun sendSealedState(hostThreadId: Long, receiveContext: ReceiveContext) {
            // For every client that has outbound mail, its last seen state ID needs to be updated to the new state ID.
            for (outboundClient in receiveContext.outboundClients) {
                lastSeenStateIds[outboundClient] = receiveContext.stateId
            }

            // TODO Add padding to the sealed state blobs: https://r3-cev.atlassian.net/browse/CON-620
            val serialised = writeData {
                writeByte(1)  // Version
                write(receiveContext.stateId.bytes)
                Instant.now().also {
                    writeLong(it.epochSecond)
                    writeInt(it.nano)
                }
                var persistentMapBytesWritten: Long = 0
                writeMap(_persistentMap) { key, value ->
                    val streamPositionStart = this.size()
                    try {
                        writeUTF(key)
                    } catch (e: UTFDataFormatException) {
                        // TODO Check the key size upon insertion rather than here so that the user has better context
                        //  of the offending key.
                        throw IllegalArgumentException(
                                "The persistent map does not support keys which are bigger " +
                                "than 65535 bytes when UTF-8 encoded.")
                    }
                    writeIntLengthPrefixBytes(value)
                    persistentMapBytesWritten += this.size() - streamPositionStart
                    check(persistentMapBytesWritten <= env.maxPersistentMapSize) {
                            "The persistent map capacity has been exceeded. To increase the size of the " +
                            "persistent map for your project, add \"def maxPersistentMapSize = <size>\" to your " +
                            "enclave build.gradle. For more information on the persistent map and the " +
                            "consequences of increasing it's size, consult the Conclave documentation."
                    }
                }
                writeMap(lastSeenStateIds) { clientPublicKey, lastSeenStateId ->
                    write(clientPublicKey.encoded)
                    write(lastSeenStateId.bytes)
                }
            }

            val sealedState = if (env.kdsConfiguration != null) {
                encryptSealedState(PlaintextAndEnvelope(serialised))
            } else {
                env.sealData(PlaintextAndEnvelope(serialised))
            }

            sendToHost(SEALED_STATE, hostThreadId, sealedState.size) { buffer ->
                buffer.put(sealedState)
            }
        }

        private fun encryptSealedState(toBeSealed: PlaintextAndEnvelope): ByteArray {
            return EnclaveUtils.aesEncrypt(getPersistencePrivateKey(), toBeSealed)
        }

        fun callUntrustedHost(bytes: ByteArray, callback: HostCallback?): ByteArray? {
            val hostThreadId = checkNotNull(currentEnclaveCall.get()) {
                "Thread ${Thread.currentThread()} may not attempt to call out to the host outside the context of a call."
            }
            val stateManager = enclaveCalls.getValue(hostThreadId)
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
                sendToHost(UNTRUSTED_HOST, hostThreadId, bytes.size) { buffer ->
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

        private fun <T> executeReceive(hostThreadId: Long, preReceive: () -> Unit, receiveMethod: () -> T): T {
            // We do locking for the user by default, because otherwise it'd be easy to forget that the host can
            // enter on multiple threads even if you aren't prepared for it. Spotting missing thread safety would
            // require spotting the absence of something rather than the presence of something, which is hard.
            // This works even if the host calls back into the enclave on the same stack. However if the host
            // makes a call on a separate thread, it's treated as a separate call as you'd expect.
            if (!threadSafe) {
                lock.withLock {
                    enclaveStateManager.checkStateIs<Started>()
                    preReceive()
                    // Additional logic is required when the persistent map is enabled to do the following:
                    // - Emit sealed state to disk
                    // - Send sealed state IDs to clients
                    // - Keep track of clients that have been sent sealed state IDs
                    // - Prevent recursive calls to deliverMail
                    return if (env.enablePersistentMap) {
                        val receiveContext = ReceiveContext()
                        val response = executeReceive(receiveMethod, receiveContext)
                        sendSealedState(hostThreadId, receiveContext)
                        response
                    } else {
                        receiveMethod()
                    }
                }
            } else {
                lock.withLock {
                    enclaveStateManager.checkStateIs<Started>()
                    preReceive()
                    ++numberReceiveCallsExecuting
                }

                try {
                    return receiveMethod()
                } finally {
                    lock.withLock {
                        --numberReceiveCallsExecuting
                        if (numberReceiveCallsExecuting == 0) {
                            enclaveQuiescentCondition.signal()
                        }
                    }
                }
            }
        }

        private fun <T> executeReceive(receiveMethod: () -> T, receiveContext: ReceiveContext): T {
            check(currentReceiveContext == null) {
                "deliverMail cannot be called in a callback to another deliverMail when the persistent map is enabled."
            }
            currentReceiveContext = receiveContext
            val response = try {
                receiveMethod()
            } finally {
                currentReceiveContext = null
            }
            check(receiveContext.pendingPostMails == 0) {
                "There were ${receiveContext.pendingPostMails} mail(s) created which were not posted with postMail."
            }
            return response
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
            val hostThreadId = checkNotNull(currentEnclaveCall.get()) {
                "Thread ${Thread.currentThread()} may not attempt to send mail outside the context of a callEnclave " +
                        "or deliverMail."
            }
            val routingHintBytes = routingHint?.toByteArray()
            val size = nullableSize(routingHintBytes) { it.intLengthPrefixSize } + encryptedBytes.size
            sendToHost(MAIL, hostThreadId, size) { buffer ->
                buffer.putNullable(routingHintBytes) { putIntLengthPrefixBytes(it) }
                buffer.put(encryptedBytes)
            }
            currentReceiveContext?.run { pendingPostMails-- }
        }
    }

    private class SequenceWatermark {
        // The -1 default allows us to check the first mail in this sequence is zero.
        private var value = -1L

        fun checkOrdering(mail: EnclaveMail) {
            val expected = value + 1
            check(mail.sequenceNumber == expected) {
                when {
                    value == -1L -> {
                        "First time seeing mail with topic ${mail.topic} so the sequence number must be zero but " +
                                "is instead ${mail.sequenceNumber}. It may be the host is delivering mail out of order."
                    }
                    mail.sequenceNumber < expected -> {
                        "Mail with sequence number ${mail.sequenceNumber} on topic ${mail.topic} has already " +
                                "been seen, was expecting $expected instead. Make sure the same PostOffice instance " +
                                "is used for the same sender key and topic, or if the sender key is long-term then a " +
                                "per-process topic is used. Otherwise it may be the host is replaying older messages."
                    }
                    else -> {
                        "Next sequence number on topic ${mail.topic} should be $expected but is instead " +
                                "${mail.sequenceNumber}. It may be the host is delivering mail out of order."
                    }
                }
            }
            value++
        }
    }

    /**
     * Represents an execution of [receiveMail] or [receiveFromUntrustedHost] and captures information needed to create
     * the sealed state and necessary header information that needs to be attached to any outbound mail.
     */
    private class ReceiveContext {
        val stateId = EnclaveStateId()
        val outboundClients = HashSet<PublicKey>()
        var pendingPostMails = 0
    }

    private sealed class CallState {
        class Receive(
            val callback: HostCallback?,
            @Suppress("unused") val receiveFromUntrustedHost: Boolean
        ) : CallState()

        class Response(val bytes: ByteArray) : CallState()
    }

    private lateinit var encryptionKeyPair: KeyPair

    /**
     * Invoked when a mail has been delivered by the host (via `EnclaveHost.deliverMail`), successfully decrypted
     * and authenticated (so the [EnclaveMail.authenticatedSender] property is reliable).
     *
     * Default implementation throws [UnsupportedOperationException] so you should not
     * perform a supercall.
     *
     * Any uncaught exceptions thrown by this method propagate to the calling `EnclaveHost.deliverMail`. In Java, checked
     * exceptions can be made to propagate by rethrowing them in an unchecked one.
     *
     * @param mail Access to the decrypted/authenticated mail body+envelope.
     * @param routingHint An optional string provided by the host that can be passed to [postMail] to tell the
     * host that you wish to reply to whoever provided it with this mail (e.g. connection ID). Note that this may
     * not be the same as the logical sender of the mail if advanced anonymity techniques are being used, like
     * users passing mail around between themselves before it's delivered.
     */
    protected open fun receiveMail(mail: EnclaveMail, routingHint: String?) {
        throw UnsupportedOperationException("This enclave does not support receiving mail.")
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
     */
    protected fun postOffice(destinationPublicKey: PublicKey, topic: String): EnclavePostOffice {
        return getCachedPostOffice(destinationPublicKey, topic, null)
    }

    /**
     * Returns a post office for mail targeted at the given destination key, and having the topic "default". The post office
     * is setup with the enclave's private encryption key so the recipient can be sure mail originated from this enclave.
     *
     * The enclave will cache post offices so that the same instance is used for the same public key and topic. This
     * ensures mail is sequenced correctly.
     *
     * The recipient should use [PostOffice.decryptMail] to make sure it verifies this enclave as the sender of the mail.
     *
     * If the destination is an enclave then use the overload which takes in an [EnclaveInstanceInfo] instead.
     */
    protected fun postOffice(destinationPublicKey: PublicKey): EnclavePostOffice {
        return postOffice(destinationPublicKey, "default")
    }

    /**
     * Returns a post office for responding back to the sender of the given mail. The mail's topic is used and the
     * authenticated sender as the destination public key.
     */
    protected fun postOffice(mail: EnclaveMail): EnclavePostOffice {
        val kdsPrivateKey = (mail as DecryptedEnclaveMail).kdsPrivateKey
        return if (kdsPrivateKey == null) {
            getCachedPostOffice(mail.authenticatedSender, mail.topic, null)
        } else {
            // If the mail was encrypted with a custom KDS private key then return a new post office instance with that
            // key each time. There's no benefit to caching KdsEnclavePostOffice since the KDS response mail do not
            // have an increasing sequence number (they are all set to 0). Caching would also cause problems if the
            // client uses another KDS key for the same (authenticatedSender, topic) pair.
            KdsEnclavePostOffice(mail.authenticatedSender, mail.topic, kdsPrivateKey)
        }
    }

    /**
     * Returns a post office for mail targeted to an enclave with the given topic. The target enclave can be one running
     * on this host or on another machine. The post office is setup with the enclave's private encryption key so the recipient
     * can be sure mail originated from this enclave.
     *
     * The enclave will cache post offices so that the same instance is used for the same [EnclaveInstanceInfo] and topic.
     * This ensures mail is sequenced correctly.
     *
     * The recipient should use [PostOffice.decryptMail] to make sure it verifies this enclave as the sender of the mail.
     */
    protected fun postOffice(enclaveInstanceInfo: EnclaveInstanceInfo, topic: String): EnclavePostOffice {
        enclaveInstanceInfo as EnclaveInstanceInfoImpl
        return getCachedPostOffice(enclaveInstanceInfo.encryptionKey, topic, enclaveInstanceInfo.keyDerivation)
    }

    /**
     * Returns a post office for mail targeted to an enclave with the topic "default". The target enclave can be one running
     * on this host or on another machine. The post office is setup with the enclave's private encryption key so the recipient
     * can be sure mail did indeed originate from this enclave.
     *
     * The enclave will cache post offices so that the same instance is used for the same [EnclaveInstanceInfo] and topic.
     * This ensures mail is sequenced correctly.
     *
     * The recipient should use [PostOffice.decryptMail] to make sure it verifies this enclave as the sender of the mail.
     */
    protected fun postOffice(enclaveInstanceInfo: EnclaveInstanceInfo): EnclavePostOffice {
        return postOffice(enclaveInstanceInfo, "default")
    }

    private fun getCachedPostOffice(
        destinationPublicKey: PublicKey,
        topic: String,
        keyDerivation: ByteArray?
    ): SessionEnclavePostOffice {
        synchronized(postOffices) {
            return postOffices.computeIfAbsent(PublicKeyAndTopic(destinationPublicKey, topic)) {
                SessionEnclavePostOffice(destinationPublicKey, topic, keyDerivation)
            }
        }
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
     * Note that posting mail is transactional. The delivery will
     * only actually take place once the current enclave call or [receiveMail] call
     * is finished. All posts (along with the enclave's sealed state that is automatically generated) take place atomically.
     */
    protected fun postMail(encryptedMail: ByteArray, routingHint: String?) {
        enclaveMessageHandler.postMail(encryptedMail, routingHint)
    }

    /**
     * [EnclavePostOffice] for creating response mail using the enclave's random session key.
     */
    private inner class SessionEnclavePostOffice(
        destinationPublicKey: PublicKey,
        topic: String,
        override val keyDerivation: ByteArray?,
    ) : EnclavePostOffice(destinationPublicKey, topic) {
        private var sequenceNumber: Long = 0

        init {
            minSizePolicy = defaultMinSizePolicy
        }

        override val nextSequenceNumber: Long get() = sequenceNumber

        override fun getAndIncrementSequenceNumber(): Long = sequenceNumber++

        override val senderPrivateKey: PrivateKey get() = encryptionKeyPair.private

        override val privateHeader: ByteArray? get() {
            return enclaveMessageHandler.currentReceiveContext?.let { receiveContext ->
                receiveContext.pendingPostMails++
                getMailPrivateHeader(receiveContext, destinationPublicKey)
            }
        }
    }

    /**
     * [EnclavePostOffice] for creating response mail using the KDS private key the recipient had specified. The
     * sequence number of these response mail are all set to 0. This is because there may be more than one enclave
     * instance involved when KDS mail is sent from the client and thus an ordering on them cannot be enforced.
     *
     * @property senderPrivateKey The KDS private key that was used to decrypt the incoming the KDS mail and which is
     * now used as the sender key for any response mail.
     */
    private inner class KdsEnclavePostOffice(
        destinationPublicKey: PublicKey,
        topic: String,
        override val senderPrivateKey: PrivateKey,
    ) : EnclavePostOffice(destinationPublicKey, topic) {
        init {
            minSizePolicy = defaultMinSizePolicy
        }
        override val nextSequenceNumber: Long get() = 0
        override fun getAndIncrementSequenceNumber(): Long = 0
        override val keyDerivation: ByteArray? get() = null
        override val privateHeader: ByteArray? get() = null
    }

    // By default let all post office instances use the same moving average instance to make it harder to analyse mail
    // sizes within any given topic.
    private val defaultMinSizePolicy = MinSizePolicy.movingAverage()

    private data class PublicKeyAndTopic(val publicKey: PublicKey, val topic: String)

    private sealed class EnclaveState {
        object New : EnclaveState()
        object Started : EnclaveState()
        object Closed : EnclaveState()
    }
}

// Typealias to make this code easier to read.
private typealias HostCallback = Function<ByteArray, ByteArray?>
