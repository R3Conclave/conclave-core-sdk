package com.r3.conclave.client

import com.r3.conclave.client.EnclaveClient.State.*
import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.EnclaveException
import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.InvalidEnclaveException
import com.r3.conclave.common.internal.StateManager
import com.r3.conclave.mail.*
import com.r3.conclave.mail.internal.*
import com.r3.conclave.mail.internal.postoffice.AbstractPostOffice
import com.r3.conclave.utilities.internal.*
import java.io.Closeable
import java.io.IOException
import java.security.PrivateKey
import java.security.PublicKey
import java.util.concurrent.ScheduledExecutorService

/**
 * Represents a client of an enclave. [EnclaveClient] manages the encryption of requests that
 * need to be sent to an enclave ([sendMail]), and using an [EnclaveTransport], transports encrypted mail to the
 * host for delivery to the enclave. It also provides a polling mechanism with [pollMail] for retrieving any
 * asychronous responses from the enclave.
 *
 * Creating a new client instance requires providing an [EnclaveConstraint] object. This acts as an identity of the
 * enclave and is used to check that the client is communicating with the intended one. A private encryption key is also
 * required. There is an constructor overload which creates a random new one for you, or you can use
 * [Curve25519PrivateKey.random] to create a new random one yourself.
 *
 * After creating a new client, users will need to call [start] and provide an [EnclaveTransport] for communicating with
 * the host. Which implemenation of [EnclaveTransport] is used will depend on the network transport between the client
 * and host. If the host is using the `conclave-web-host` server then users can use
 * [com.r3.conclave.client.web.WebEnclaveTransport] for the transport.
 *
 * ### Sending and receiving Mail
 *
 * After starting the client is able to send mail to the enclave using [sendMail]. [EnclaveClient] will encrypt the body
 * and optional envelope with the private encryption key and then pass the encrypted bytes onto the provided
 * [EnclaveTransport] to deliver it to the host. [sendMail] can optionally return back a decrypted [EnclaveMail] which
 * is the synchronous response from the enclave, if it produced one.
 *
 * For receiving asychronous responses, i.e. those which the enclave produces for the client but which are produced
 * due to other clients, [pollMail] will return the next asynchronous response if one is available.
 *
 * ### Thread safety
 *
 * [EnclaveClient] is not thread-safe. [sendMail] and [pollMail] must be called from the same thread. If the client
 * wants to poll for asychronous mail in a background thread then that same thread must also be used for sending mail.
 * A simple way to achieve this is to use a single-threaded [ScheduledExecutorService]. The following (incomplete) code
 * sample shows how this might be done.
 *
 * ```java
 *     ScheduledExecutorService singleThreadWorker = Executors.newSingleThreadScheduledExecutor();
 *     BlockingQueue<EnclaveMail> receivedMail = new LinkedBlockingQueue<>();
 *
 *     private ScheduledFuture<?> pollingTask;
 *
 *     void start() throws InvalidEnclaveException, IOException {
 *          enclaveClient.start(enclaveTransport);
 *          pollingTask = singleThreadWorker.scheduleAtFixedRate(() -> {
 *              try {
 *                  EnclaveMail mail = enclaveClient.pollMail();
 *                  if (mail != null) {
 *                      receivedMail.add(mail);
 *                  }
 *              } catch (IOException e) {
 *                  throw new UncheckedIOException(e);
 *              }
 *          }, 0, 5, TimeUnit.SECONDS);  // Poll every 5 seconds
 *     }
 *
 *     Future<EnclaveMail> sendMail(byte[] body) {
 *          return singleThreadWorker.submit(() -> enclaveClient.sendMail(body));
 *     }
 *
 *     void close() throws IOException {
 *          if (pollTask != null)
 *              pollingTask.cancel(true);
 *          enclaveClient.close();
 *     }
 * ```
 *
 * ### Enclave rollback
 *
 * One key security feature [EnclaveClient] provides is detection of rollback attacks by the host on the enclave. This
 * type of attack is where the host restarts the enclave but in an older state. The enclave itself is unable to detect
 * this but it can leverage its clients so that they may detect it. However not all clients may detect the rollback, but
 * at least some will.
 *
 * Detection is enabled by default in [EnclaveClient]. However it's only detected when the client next receives mail
 * which is why [sendMail] and [pollMail] can throw [EnclaveRollbackException]. What action needs to be taken depends on
 * the client's security policy but it may include contacting other clients (if any are known) or maybe even the enclave
 * host if there is genuine foul play and they can be brought to account. It's also important to note a rollback can
 * arise from a genuine mistake if the host accidently restarted the enclave from an old state.
 *
 * The client can continue to be used to send and receive mail after an [EnclaveRollbackException] is thrown. However
 * the state of the client and the state of the enclave are probably now out of sync and further communication with the
 * enclave may be unsafe. If rollback attacks are not a concern at all then detection for it can be turned off
 * completely by overriding [ignoreEnclaveRollback] to return true. The same security caveats apply.
 *
 * ### Restarts
 *
 * [EnclaveClient] is designed to handle both enclave and client restarts. If the enclave restarts then its encryption
 * key changes but [EnclaveClient] will automatically switch to the new encryption key when it detects this.
 * Notifications for restarts can be enabled by overriding [onEnclaveRestarted]. This might be needed if the enclave is
 * non-persistent and the client needs to re-submit their data. It could also be used as an indication of a rollback
 * attack.
 *
 * To support client restarts the state of [EnclaveClient] must be persisted and then restored. Failing to do this will
 * prevent the client from sending or receive mail due to spurious errors. To this end, [save] can be used to serialize
 * the state of the client which can be restored using the [EnclaveClient] constructor that takes in a byte array.
 * This is provided solely as a convenience and the state of the client can be persisted any other way. See the [save]
 * documentation for more details.
 *
 * ### PostOffice
 *
 * [EnclaveClient] is an extension to [PostOffice] and provides features it cannot support. If these features are not
 * needed and you simply need to send a one-off mail, for example, then use [EnclaveInstanceInfo.createPostOffice] to
 * create a post office for encrypting requests and decrypting any responses.
 *
 * @see EnclaveTransport
 */
open class EnclaveClient private constructor(
    clientPrivateKey: PrivateKey?,
    enclaveConstraint: EnclaveConstraint?,
    savedClient: ByteArray?
) : Closeable {
    /** Creates a new client with a new random private key. */
    constructor(enclaveConstraint: EnclaveConstraint) : this(Curve25519PrivateKey.random(), enclaveConstraint, null)

    /** Creates a new client with an existing private key. */
    constructor(clientPrivateKey: PrivateKey, enclaveConstraint: EnclaveConstraint) : this(
        clientPrivateKey,
        enclaveConstraint,
        null
    )

    /**
     * Creates a new client from the serialized state of a previous one that used [save].
     * @throws IllegalArgumentException If the bytes do not represent an enclave client or is corrupted.
     */
    constructor(savedClient: ByteArray) : this(null, null, savedClient)

    /**
     * Returns the private key that is used to encrypt mail to send to the enclave.
     */
    val clientPrivateKey: PrivateKey

    /**
     * The constraints the enclave must satisfy before this client will communicate with it.
     */
    var enclaveConstraint: EnclaveConstraint

    private val stateManager: StateManager<State>
    private val _postOffices = HashMap<String, PostOffice>()
    private var _lastSeenStateId: EnclaveStateId? = null

    init {
        if (savedClient == null) {
            this.clientPrivateKey = clientPrivateKey!!
            this.enclaveConstraint = enclaveConstraint!!
            stateManager = StateManager(New(null, emptyMap()))
        } else {
            val dis = savedClient.dataStream()
            try {
                require(dis.readExactlyNBytes(MAGIC.size).contentEquals(MAGIC)) { "Not a serialized enclave client" }
                val version = dis.read()
                require(version == 1)
                this.clientPrivateKey = Curve25519PrivateKey(dis.readExactlyNBytes(32))
                this.enclaveConstraint = EnclaveConstraint.parse(dis.readUTF())
                val previousEnclaveKey = dis.nullableRead { Curve25519PublicKey(readExactlyNBytes(32)) }
                _lastSeenStateId = dis.nullableRead { readEnclaveStateId() }
                val previousSequenceNumbers = HashMap<String, Long>()
                repeat(dis.readInt()) {
                    val topic = dis.readUTF()
                    val sequenceNumber = dis.readLong()
                    previousSequenceNumbers[topic] = sequenceNumber
                }
                stateManager = StateManager(New(previousEnclaveKey, previousSequenceNumbers))
            } catch (e: IOException) {
                throw IllegalArgumentException("Corrupted serialized enclave client", e)
            }
        }
    }

    /**
     * Returns the corresponding public key of [clientPrivateKey]. This will be what the enclave sees as the
     * authenticated sender of mail it receives from this client.
     *
     * @see EnclaveMail.authenticatedSender
     */
    val clientPublicKey: PublicKey get() = privateCurve25519KeyToPublic(clientPrivateKey)

    /**
     * Returns the [EnclaveTransport] used to communicate with the host.
     * @throws IllegalStateException If the client has not been started.
     */
    val transport: EnclaveTransport get() = currentOrPreviousRunningState.transport

    /**
     * Returns the most recent [EnclaveInstanceInfo] downloaded from the host via the transport.
     * @throws IllegalStateException If the client has not been started.
     */
    val enclaveInstanceInfo: EnclaveInstanceInfo get() = currentOrPreviousRunningState.enclaveInstanceInfo

    /**
     * Returns this client's [EnclaveTransport.ClientConnection] instance with the enclave transport.
     * @throws IllegalStateException If the client has not been started.
     */
    val clientConnection: EnclaveTransport.ClientConnection get() = currentOrPreviousRunningState.clientConnection

    /**
     * Starts the client with the given [EnclaveTransport].
     *
     * @throws IOException If a connection could not be made to the host via the transport.
     * @throws InvalidEnclaveException If the enclave's [EnclaveInstanceInfo] does not satisfy the client's constraints.
     * @throws IllegalStateException If the client has already been started or has been closed.
     */
    @Throws(IOException::class, InvalidEnclaveException::class)
    fun start(transport: EnclaveTransport) {
        val newState = stateManager.checkStateIs<New> { "The client has not been started or has been closed." }
        val enclaveInstanceInfo = transport.enclaveInstanceInfo()
        enclaveConstraint.check(enclaveInstanceInfo)
        if (newState.previousEnclaveKey == enclaveInstanceInfo.encryptionKey) {
            for ((topic, sequenceNumber) in newState.previousSequenceNumbers) {
                val postOffice = enclaveInstanceInfo.createPostOffice(clientPrivateKey, topic)
                postOffice.setNextSequenceNumber(sequenceNumber)
                _postOffices[topic] = postOffice
            }
        } else {
            // If the enclave's key has changed (i.e. it has restarted) since the last time the client was running then
            // it will be expecting sequence number zero. However there's no point creating such a PostOffice now. It
            // can be created if and when the user next uses those topics.
        }

        val clientConnection = transport.connect(this)
        stateManager.state = Running(transport, clientConnection, enclaveInstanceInfo)
    }

    /**
     * Encrypt and send a mail message with the given body to the host using the transport for delivery to the enclave.
     * The mail will have a topic of "default" and an empty envelope.
     *
     * This method will block until the enclave has processed the mail successfully. If the enclave synchronously
     * responds back with mail then they will be decrypted and returned back here. Any responses the enclave produces
     * asychronously after the mail has been produced will be returned by [pollMail].
     *
     * If the enclave throws an exception during the processing of the request mail then this method will throw an
     * [EnclaveException]. The message from the original enclave exception may or may not be present. In particular, if
     * the enclave is running in release mode then the message will never be present. This is to prevent any potential
     * secrets from being leaked.
     *
     * @param body The body of the mail that is to be encrypted with the client's private key.
     *
     * @throws IOException If the mail could not be sent to the host or the response could not be processed.
     * @throws EnclaveException If the enclave threw an exception.
     * @throws EnclaveRollbackException If the client has detected that the enclave's state has been rolled back.
     * @throws IllegalStateException If the client is not running.
     *
     * @see EnclaveMail
     */
    @Throws(IOException::class)
    fun sendMail(body: ByteArray): EnclaveMail? = sendMail("default", body, null)

    /**
     * Encrypt and send a mail message with the given body to the host using the transport for delivery to the enclave.
     * It will have the given topic and envelope.
     *
     * This method will block until the enclave has processed the mail successfully. If the enclave synchronously
     * responds back with mail then it will be decrypted and returned back here. Any responses the enclave produces
     * asychronously after the mail has been produced need to be polled for using [pollMail].
     *
     * If the enclave threw an exception during the processing of the request mail then this method will throw an
     * [EnclaveException]. The message from the original enclave exception may or may not be present. In particular, if
     * the enclave is running in release mode then the message will never be present. This is to prevent any potential
     * secrets from being leaked.
     *
     * @param topic The topic to use in the mail. See [EnclaveMail.topic].
     * @param body The body of the mail that is to be encrypted with the client's private key.
     * @param envelope Optional visible, but authenticated, portion of the mail. See [EnclaveMail.envelope].
     *
     * @throws IOException If the mail could not be sent to the host or the response could not be processed.
     * @throws EnclaveException If the enclave threw an exception.
     * @throws EnclaveRollbackException If the client has detected that the enclave's state has been rolled back.
     * @throws IllegalStateException If the client is not running.
     *
     * @see EnclaveMail
     */
    @Throws(IOException::class)
    fun sendMail(topic: String, body: ByteArray, envelope: ByteArray?): EnclaveMail? {
        val runningState = stateManager.checkStateIs<Running> { "The client is not running." }
        val (transport, clientHandle) = runningState

        for (i in 0 until MAX_RETRY_ATTEMPTS) {
            val encryptedMailBytes = postOffice(topic).encryptMail(body, envelope)

            val response = try {
                clientHandle.sendMail(encryptedMailBytes)
            } catch (e: MailDecryptionException) {
                // The enclave was unable to decrypt our mail. Hopefully it's because the enclave was restarted and thus
                // has a new encryption key. Let's re-download the EII and try again with the new key.
                val newEnclaveInstanceInfo = transport.enclaveInstanceInfo()
                if (newEnclaveInstanceInfo.encryptionKey == runningState.enclaveInstanceInfo.encryptionKey) {
                    // Turns out the enclave's key hasn't changed, which means something else has happened, probably a
                    // bug in the transport layer not picking up the new EII. Either way the exception needs to be
                    // propagated to the caller.
                    throw IOException(e)
                }
                try {
                    enclaveConstraint.check(newEnclaveInstanceInfo)
                } catch (e: InvalidEnclaveException) {
                    throw IOException("The enclave has a new EnclaveInstanceInfo which no longer satisfies the " +
                            "client's constraints", e)
                }
                // All existing post office instances are now invalid as they're using the old encryption key.
                resetPostOffices(newEnclaveInstanceInfo)
                runningState.enclaveInstanceInfo = newEnclaveInstanceInfo
                onEnclaveRestarted()
                continue
            }

            return response?.let { processMail(it, runningState.enclaveInstanceInfo) }
        }

        // If we get here it's then reasonable to assume something is wrong with the host/transport and we need to abort.
        throw IOException("Aborted attempt to send mail as the enclave has been restarted several times whilst " +
                "trying to send it mail.")
    }

    /**
     * Polls the host for the next asynchronous mail response from the enclave, if there is one, and returns it
     * decrypted. Otherwise returns `null`.
     *
     * @return The next asynchronous mail response decrypted, or `null` if there isn't one.
     * @throws IOException If the client is unable to poll the host or retrieve the mail.
     * @throws EnclaveRollbackException If the client has detected that the enclave's state has been rolled back.
     * @throws IllegalStateException If the client is not running.
     */
    @Throws(IOException::class)
    fun pollMail(): EnclaveMail? {
        val (_, clientHandle, enclaveInstanceInfo) = stateManager.checkStateIs<Running> { "The client is not running." }
        val responseBytes = clientHandle.pollMail()
        return responseBytes?.let { processMail(it, enclaveInstanceInfo) }
    }

    /**
     * Determines whether the client should continue processing mail it's getting from host if it detects the enclave's
     * state has been rolled back.
     *
     * A rollback is a type of attack the host can do on an enclave to make it think it is dealing with up-to-date data
     * when in fact it's been given old out-of-date data. Due to the nature of enclaves they have no way of detecting
     * this themeselves and so must rely on their clients to detect for them.
     *
     * The default implementation returns false which means the client will not process the mail and [sendMail] and
     * [pollMail] will throw an [EnclaveRollbackException]. Override this method if you wish to change this. You can
     * connect this method to a UI, for example, if the decision needs to be made by the user.
     *
     * @return `true` to ignore the detected rollback and continue processing the mail. `false` to stop processing mail
     * and have [EnclaveRollbackException] be thrown by [sendMail] and [pollMail].
     *
     * @see EnclaveRollbackException
     */
    protected open fun ignoreEnclaveRollback(): Boolean = false

    /**
     * Called when the client detects the enclave has been restarted. Override this method to get notification of any
     * restarts. The default implementation does nothing.
     *
     * Note, it's not necessary that all restarts will be detected. Plus, [sendMail] is required before a restart can be
     * detected.
     */
    protected open fun onEnclaveRestarted() {

    }

    /**
     * Returns the last seen state ID from the enclave. The state ID is tracked to detect attempts to roll back the
     * state of the enclave by the host.
     *
     * Access to this value is typically only needed to persist it if a custom serialisation scheme is used (i.e. not
     * [save]). Make sure to restore this value on the new client object to ensure it can continue communicating with
     * the enclave.
     */
    var lastSeenStateId: ByteArray?
        set(value) {
            _lastSeenStateId = value?.let { EnclaveStateId(it.clone()) }
        }
        get() = _lastSeenStateId?.bytes?.clone()

    /**
     * Returns the [PostOffice] instance for the given topic, creating a new one if one doesn't already exist.
     * @throws IllegalStateException If the client has not been started.
     */
    fun postOffice(topic: String): PostOffice {
        return _postOffices.computeIfAbsent(topic) {
            enclaveInstanceInfo.createPostOffice(clientPrivateKey, topic)
        }
    }

    /**
     * Returns the set of [PostOffice]s that have been used to send mail.
     *
     * The returned [Set] is a copy. Adding or removing from it does not affect the client.
     */
    val postOffices: Set<PostOffice> get() = _postOffices.values.toSet()

    /**
     * Serializes the state of the client to a byte array so that it can be safely persisted and restored if the client is
     * restarted. Use [EnclaveClient.restoreState] to materialise the state again. As the private key is also serialized
     * it's vitally important the bytes are persisted securely or are encrypted.
     *
     * It is recommended that state be saved after every [sendMail] and [pollMail] call.
     *
     * This method is just a convenience and you may use your own serialization scheme to persist the enclave's state.
     * To ensure the client can continue communicating with the enclave if the client is restarted it's important the
     * following is preserved:
     *
     * * The client's private key ([clientPrivateKey])
     * * The enclave's state ID ([lastSeenStateId])
     * * The next sequence number for every topic used. Use [postOffices] for this.
     *
     * @see EnclaveClient.restoreState
     */
    fun save(): ByteArray {
        val state = stateManager.state
        val enclaveKey = when (state) {
            is New -> state.previousEnclaveKey
            is Running -> state.enclaveInstanceInfo.encryptionKey
            is Closed -> state.running?.enclaveInstanceInfo?.encryptionKey
        }
        val sequenceNumbers = when (state) {
            is New -> state.previousSequenceNumbers.map { it.toPair() }
            else -> _postOffices.map { Pair(it.key, it.value.nextSequenceNumber) }
        }
        return writeData {
            write(MAGIC)
            write(1)  // Version
            write(clientPrivateKey.encoded)
            writeUTF(enclaveConstraint.toString())
            nullableWrite(enclaveKey) { write(it.encoded) }
            nullableWrite(_lastSeenStateId) { write(it.bytes) }
            writeList(sequenceNumbers) { (topic, sequenceNumber) ->
                writeUTF(topic)
                writeLong(sequenceNumber)
            }
        }
    }

    /**
     * Closes the client and disconnects it from the enclave transport. This is a no-op if the client is already closed.
     */
    @Throws(IOException::class)
    override fun close() {
        val runningState = when (val state = stateManager.state) {
            is New -> null
            is Running -> state
            is Closed -> return
        }
        stateManager.state = Closed(runningState)
        runningState?.clientConnection?.disconnect()
    }

    private fun processMail(
        encryptedMail: ByteArray,
        enclaveInstanceInfo: EnclaveInstanceInfo,
    ): DecryptedEnclaveMail {
        val mail = try {
            AbstractPostOffice.decryptMail(
                encryptedMail,
                clientPrivateKey,
                enclaveInstanceInfo.encryptionKey
            )
        } catch (e: MailDecryptionException) {
            throw IOException("Unable to decrypt received mail", e)
        }

        // See if the mail has a private header. If it does then the enclave has been configured for rollback detection
        // and has sent us the necessary information to detect if the host has rolled back its state.
        mail.privateHeader?.deserialise {
            val version = read()
            check(version == 1)
            val receivedStateId = readEnclaveStateId()
            val expectedPreviousStateId = nullableRead { readEnclaveStateId() }

            val previousStateId = _lastSeenStateId
            if (receivedStateId != previousStateId) {
                // We update the last seen state first so that the client can continue to receive mail if they wish
                // after the exception is thrown.
                _lastSeenStateId = receivedStateId
                if (previousStateId != expectedPreviousStateId && !ignoreEnclaveRollback()) {
                    throw EnclaveRollbackException("Possible dropped mail or enclave state rollback by the host " +
                            "detected. Expected $_lastSeenStateId but got $expectedPreviousStateId.", mail)
                }
            } else {
                // If the state ID hasn't changed then it probably means the enclave has sent multiple mail from the
                // same receiveMail/receiveFromUntrustedHost invocation. Or it could mean the host is replaying the same
                // mail to us. We may want to add replay, re-order and dropped mail detection support. But this is
                // something slightly different to roll back detection and would need to be handled separately, probably
                // by checking the sequence numbers. https://r3-cev.atlassian.net/browse/CON-625
            }
        }

        return mail
    }

    private val currentOrPreviousRunningState: Running get() {
        return when (val state = stateManager.state) {
            is New -> throw IllegalStateException("Client has not been started.")
            is Running -> state
            is Closed -> checkNotNull(state.running) { "Client was never started." }
        }
    }

    private fun resetPostOffices(enclaveInstanceInfo: EnclaveInstanceInfo) {
        // Replace any existing post offices with new ones from the current EnclaveInstanceInfo whilst preserving the
        // min size policies. The sequence numbers are reset to zero.
        _postOffices.replaceAll { topic, old ->
            enclaveInstanceInfo.createPostOffice(clientPrivateKey, topic).apply {
                minSizePolicy = old.minSizePolicy
            }
        }
    }


    private sealed class State {
        data class New(
            val previousEnclaveKey: Curve25519PublicKey?,
            val previousSequenceNumbers: Map<String, Long>
        ) : State()

        data class Running(
            val transport: EnclaveTransport,
            val clientConnection: EnclaveTransport.ClientConnection,
            var enclaveInstanceInfo: EnclaveInstanceInfo
        ) : State()

        data class Closed(val running: Running?) : State()
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 10
        private val MAGIC = "EnclaveClient".toByteArray()
    }
}
