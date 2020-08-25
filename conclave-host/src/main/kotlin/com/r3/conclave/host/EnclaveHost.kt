package com.r3.conclave.host

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.EnclaveCall
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.ErrorHandler
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.common.internal.handler.SimpleMuxingHandler
import com.r3.conclave.host.EnclaveHost.CallState.*
import com.r3.conclave.host.EnclaveHost.HostState.*
import com.r3.conclave.host.internal.*
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.EnclaveMailId
import com.r3.conclave.utilities.internal.*
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * Represents an enclave running on the local CPU. Instantiating this object loads and
 * initialises the enclave, making it ready to receive connections.
 *
 * You can get a [EnclaveHost] using one of the static factory methods.
 *
 * An enclave won't actually be loaded and initialised immediately until the [start] method is explicitly called.
 * This gives you time to configure the [EnclaveHost] before startup.
 *
 * Multiple enclaves can be loaded at once, however, you may not mix
 * simulation/debug/production enclaves together.
 *
 * Although the enclave must currently run against Java 8, the host can use any
 * version of Java that is supported.
 */
// The constructor is only protected to make it harder for a user to accidently create a host via "new".
open class EnclaveHost protected constructor() : AutoCloseable {
    /**
     * Suppress kotlin specific companion objects from our API documentation.
     * The public items within the object are still published in the documentation.
     * @suppress
     */
    companion object {
        private val log = loggerFor<EnclaveHost>()
        private val signatureScheme = SignatureSchemeEdDSA()

        internal fun create(enclaveHandle: EnclaveHandle<ErrorHandler.Connection>): EnclaveHost {
            val host = EnclaveHost()
            init(host, enclaveHandle)
            return host
        }

        internal fun init(host: EnclaveHost, enclaveHandle: EnclaveHandle<ErrorHandler.Connection>) {
            host.enclaveHandle = enclaveHandle
        }

        /**
         * Load the signed enclave for the given enclave class name.
         *
         * @throws IllegalArgumentException if there is no enclave file for the given class name.
         * @throws IllegalStateException if multiple enclave files were found for the given class name.
         * @throws EnclaveLoadException if the enclave does not load correctly or if the platform does
         *                              not support hardware enclaves or if enclave support is disabled.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class)
        fun load(enclaveClassName: String): EnclaveHost {
            val (stream, enclaveMode) = findEnclaveFile(enclaveClassName)
            val enclaveFile = try {
                Files.createTempFile(enclaveClassName, "signed.so")
            } catch (e: Exception) {
                throw EnclaveLoadException("Unable to load enclave", e)
            }
            try {
                stream.use { Files.copy(it, enclaveFile, REPLACE_EXISTING) }
                return createHost(enclaveMode, enclaveFile, enclaveClassName, tempFile = true)
            } catch (e: UnsatisfiedLinkError) {
                // We get an unsatisifed link error if the native library could not be loaded on
                // the current platform - this will happen if the user tries to load an enclave
                // on a platform other than Linux.
                enclaveFile.deleteQuietly()
                throw EnclaveLoadException("Enclaves may only be loaded on Linux hosts.")
            } catch (e: Exception) {
                enclaveFile.deleteQuietly()
                throw if (e is EnclaveLoadException) e else EnclaveLoadException("Unable to load enclave", e)
            }
        }

        /**
         * Searches for the single enclave file in the classpath at /package/namespace/classname-mode.signed.so. For
         * example it will look for the enclave file of "com.foo.bar.Enclave" at /com/foo/bar/Enclave-$mode.signed.so.
         * If more than one file is found (i.e. multiple modes) then an exception is thrown.
         *
         * The mode is derived from the filename but is not taken at face value. The construction of the EnclaveInstanceInfoImpl
         * in `start` makes sure the mode is correct it terms of the remote attestation.
         */
        private fun findEnclaveFile(className: String): Pair<InputStream, EnclaveMode> {
            val found = EnclaveMode.values().mapNotNull { mode ->
                val resourceName = "/${className.replace('.', '/')}-${mode.name.toLowerCase()}.signed.so"
                val url = EnclaveHost::class.java.getResource(resourceName)
                url?.let { Pair(it, mode) }
            }
            when (found.size) {
                1 -> return Pair(found[0].first.openStream(), found[0].second)
                0 -> throw IllegalArgumentException(
                        """Enclave file for $className does not exist on the classpath. Please make sure the gradle dependency to the enclave project is correctly specified:
                    |    runtimeOnly project(path: ":enclave project", configuration: mode)
                    |    
                    |    where:
                    |      mode is either "release", "debug" or "simulation"
                """.trimMargin()
                )
                else -> throw IllegalStateException("Multiple enclave files were found: $found")
            }
        }

        private fun Path.deleteQuietly() {
            try {
                Files.deleteIfExists(this)
            } catch (e: IOException) {
                // Ignore
            }
        }

        /**
         * Checks to see if the platform supports hardware based enclaves.
         *
         * This method checks to see if the CPU and the BIOS are capable of supporting enclaves and
         * whether support has been enabled.
         *
         * If enclaves are supported but not enabled some platforms allow support to be enabled via a software
         * call. This method can optionally be used to attempt to enable support on the platform.
         * Enabling enclave support via software may require the application calling this method to
         * be started with root privileges.
         *
         * @param enableSupport Set to true to attempt to enable enclave support on the platform if support is
         * currently disabled.
         *
         * @throws EnclaveLoadException if enclave support is not available on the platform. The exception message
         * gives a detailed reason why enclaves are not supported.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class)
        fun checkPlatformSupportsEnclaves(enableSupport: Boolean) {
            try {
                // Note the EnclaveLoadMode does not matter in this case as we are always checking the hardware
                NativeShared.checkPlatformSupportsEnclaves(enableSupport);
            } catch (e: EnclaveLoadException) {
                throw e
            } catch (e: UnsatisfiedLinkError) {
                // We get an unsatisifed link error if the native library could not be loaded on
                // the current platform - this will happen if the user tries to load an enclave
                // on a platform other than Linux.
                throw EnclaveLoadException("Enclaves may only be loaded on Linux hosts.")
            } catch (e: Exception) {
                throw EnclaveLoadException("Unable to check platform support", e)
            }
        }
    }

    // EnclaveHandle is an internal class and thus to prevent it from leaking into the public API this is not a c'tor parameter.
    private lateinit var enclaveHandle: EnclaveHandle<ErrorHandler.Connection>
    private val hostStateManager = StateManager<HostState>(New)
    private lateinit var adminHandler: AdminHandler
    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private lateinit var enclaveMessageHandler: EnclaveMessageHandler
    private var _enclaveInstanceInfo: EnclaveInstanceInfoImpl? = null

    private var mailCallbacks: MailCallbacks? = null

    /**
     * The mode the enclave is running in.
     */
    val enclaveMode: EnclaveMode get() = enclaveHandle.enclaveMode

    /**
     * Causes the enclave to be loaded and the `Enclave` object constructed inside.
     * This method must be called before sending is possible. Remember to call
     * [close] to free the associated enclave resources when you're done with it.
     *
     * @param spid The EPID Service Provider ID (or SPID) needed for creating the enclave quote for attesting. Please see
     * https://api.portal.trustedservices.intel.com/EPID-attestation for further details on how to obtain one. The EPID
     * signature mode must be Linkable Quotes.
     *
     * This parameter is not used if the enclave is in simulation mode (as no attestation is done in simulation) and null
     * can be provided.
     *
     * Note: This parameter is temporary and will be removed in a future version.
     *
     * @param attestationKey The private attestation key needed to access the attestation service. Please see
     * https://api.portal.trustedservices.intel.com/EPID-attestation for further details on how obtain one.
     *
     * This parameter is not used if the enclave is in simulation mode (as no attestation is done in simulation) and null
     * can be provided.
     *
     * @param mailCallbacks A callback that will be invoked when the enclave requests delivery or acknowledgement of mail.
     * If null then the enclave cannot send or acknowledge mail, although you can still deliver it.
     */
    @Throws(EnclaveLoadException::class)
    @Synchronized
    fun start(spid: OpaqueBytes?, attestationKey: String?, mailCallbacks: MailCallbacks?) {
        if (hostStateManager.state is Started) return
        hostStateManager.checkStateIsNot<Closed> { "The host has been closed." }
        require(spid == null || spid.size == 16) { "Invalid SPID length" }
        try {
            this.mailCallbacks = mailCallbacks
            // Set up a set of channels in and out of the enclave. Each byte array sent/received comes with
            // a prefixed channel ID that lets us split them out to separate classes.
            val mux: SimpleMuxingHandler.Connection = enclaveHandle.connection.setDownstream(SimpleMuxingHandler())
            // The admin handler deserializes keys and other info from the enclave during initialisation.
            adminHandler = mux.addDownstream(AdminHandler(this))
            // The EPID attestation handler manages the process of generating remote attestations that are then
            // placed into the EnclaveInstanceInfo.
            val attestationConnection = mux.addDownstream(
                    EpidAttestationHostHandler(
                            SgxQuoteType.LINKABLE,
                            spid?.let { Cursor.read(SgxSpid, it.buffer()) } ?: Cursor.allocate(SgxSpid),
                            enclaveMode == EnclaveMode.MOCK
                    )
            )
            // We request an attestation at every startup. This is because in early releases the keys were ephemeral
            // and changed on restart, but this is no longer true.
            //
            // Additionally, the enclave is initialised when it receives its first bytes. We use the request for
            // the signed quote as that trigger. Therefore we know at this point adminHandler.enclaveInfo is available
            // for us to query.
            //
            // TODO: Change this to fetch quotes on demand.
            val signedQuote = attestationConnection.getSignedQuote()
            _enclaveInstanceInfo = getAttestationService(attestationKey)
                    .doAttest(
                            adminHandler.enclaveInfo.signatureKey,
                            adminHandler.enclaveInfo.encryptionKey,
                            signedQuote,
                            enclaveMode
                    )
            // This handler wires up callUntrustedHost -> callEnclave and mail delivery.
            enclaveMessageHandler = mux.addDownstream(EnclaveMessageHandler())
            log.debug { enclaveInstanceInfo.toString() }
            hostStateManager.state = Started
        } catch (e: Exception) {
            throw EnclaveLoadException("Unable to start enclave", e)
        }
    }

    private fun getAttestationService(attestationKey: String?): AttestationService {
        return when (enclaveMode) {
            EnclaveMode.RELEASE -> IntelAttestationService(true, requiredAttestationKey(attestationKey))
            EnclaveMode.DEBUG -> IntelAttestationService(false, requiredAttestationKey(attestationKey))
            EnclaveMode.SIMULATION, EnclaveMode.MOCK -> MockAttestationService()
        }
    }

    private fun requiredAttestationKey(attestationKey: String?): String {
        return requireNotNull(attestationKey) { "$enclaveMode mode requires an attestation key" }
    }

    /**
     * Provides the info of this specific loaded instance. Note that the enclave
     * instance info will remain valid across restarts of the host JVM/reloads of the
     * enclave.
     *
     * @throws IllegalStateException if the enclave has not been started.
     */
    val enclaveInstanceInfo: EnclaveInstanceInfo
        get() = checkNotNull(_enclaveInstanceInfo) { "Enclave has not been started." }

    /**
     * Passes the given byte array to the enclave. The format of the byte
     * arrays are up to you but will typically use some sort of serialization
     * mechanism, alternatively, [DataOutputStream] is a convenient way to lay out
     * pieces of data in a fixed order.
     *
     * For this method to work the enclave class must implement [EnclaveCall]. The return
     * value of [EnclaveCall.invoke] (which can be null) is returned here. It will not
     * be received via the provided callback.
     *
     * With the provided callback the enclave also has the option of using
     * `Enclave.callUntrustedHost` and sending/receiving byte arrays in the opposite
     * direction. By chaining callbacks together, a kind of virtual stack can be constructed
     * allowing complex back-and-forth conversations between enclave and untrusted host.
     *
     * @param bytes Bytes to send to the enclave.
     * @param callback Bytes received from the enclave via `Enclave.callUntrustedHost`.
     *
     * @return The return value of the enclave's [EnclaveCall.invoke].
     *
     * @throws IllegalArgumentException If the enclave does not implement [EnclaveCall]
     * or if the host has not been started.
     */
    fun callEnclave(bytes: ByteArray, callback: EnclaveCall): ByteArray? = callEnclaveInternal(bytes, callback)

    /**
     * Passes the given byte array to the enclave. The format of the byte
     * arrays are up to you but will typically use some sort of serialization
     * mechanism, alternatively, [DataOutputStream] is a convenient way to lay out
     * pieces of data in a fixed order.
     *
     * For this method to work the enclave class must implement [EnclaveCall]. The return
     * value of [EnclaveCall.invoke] (which can be null) is returned here.
     *
     * The enclave does not have the option of using `Enclave.callUntrustedHost` for
     * sending bytes back to the host. Use the overload which takes in a [EnclaveCall]
     * callback instead.
     *
     * @param bytes Bytes to send to the enclave.
     *
     * @return The return value of the enclave's [EnclaveCall.invoke].
     *
     * @throws IllegalArgumentException If the enclave does not implement [EnclaveCall]
     * or if the host has not been started.
     */
    fun callEnclave(bytes: ByteArray): ByteArray? = callEnclaveInternal(bytes, null)

    private fun callEnclaveInternal(bytes: ByteArray, callback: EnclaveCall?): ByteArray? {
        when (hostStateManager.state) {
            New -> throw IllegalStateException("The host has not been started.")
            Started -> check(adminHandler.enclaveInfo.enclaveImplementsEnclaveCall) {
                "The enclave does not implement EnclaveCall to receive messages from the host."
            }
            Closed -> throw IllegalStateException("The host has been closed.")
        }
        return enclaveMessageHandler.callEnclave(bytes, callback)
    }

    /**
     * Delivers the given encrypted mail bytes to the enclave. If the enclave throws
     * an exception it will be rethrown.
     * It's up to the caller to decide what to do with mails that don't seem to be
     * handled properly: discarding it and logging an error is a simple option, or
     * alternatively queuing it to disk in anticipation of a bug fix or upgrade
     * is also workable.
     *
     * There is likely to be a callback on the same thread to the
     * [MailCallbacks.postMail] function, requesting mail to be sent back in response
     * and/or acknowledgement. However, it's also possible the enclave will hold
     * the mail without requesting any action.
     *
     * When an enclave is started, you must redeliver, in order, any unacknowledged
     * mail so the enclave can rebuild its internal state.
     *
     * @param id an identifier that may be passed to [MailCallbacks.acknowledgeMail]. The scope of this ID is up until
     * the enclave acknowledges the mail, so it doesn't have to be fully unique forever, nor does it need to be derived
     * from anything in particular. A good choice is a row ID in a database or a queue message ID, for example.
     * @param mail the encrypted mail received from a remote client.
     * @param callback If the enclave calls `Enclave.callUntrustedHost` then the
     * bytes will be passed to this object for consumption and generation of the
     * response.
     * @throws IllegalStateException if the enclave has not been started.
     */
    @JvmOverloads
    fun deliverMail(id: EnclaveMailId, mail: ByteArray, callback: EnclaveCall? = null) {
        enclaveMessageHandler.deliverMail(id, mail, callback)
    }

    // TODO: Rewrite MailCallbacks to expose MailCommands directly as a group, so the host can more easily process them atomically.

    /**
     * Implement this on an object you provide that binds messages from the enclave
     * back to the network or processes them locally.
     *
     * Callbacks will never be invoked concurrently as the platform provides
     * synchronisation for you.
     *
     * From an enclave writer's perspective, all the implementations of these methods
     * should be considered malicious.
     */
    interface MailCallbacks {
        /**
         * Called when the enclave wants to send an encrypted message over the network
         * to a client. The host should examine the public key and/or the
         * [routingHint] parameter to decide where the enclave wants it to be sent.
         *
         * The routing hint may be "self". In that case you are expected to send the mail
         * back to the enclave when the enclave is restarted.
         *
         * You don't have to perform the actual send synchronously if that's inappropriate
         * for your app. However, the mail must be recorded for delivery synchronously, so
         * no messages can be lost in case of crash failure.
         */
        @JvmDefault
        fun postMail(encryptedBytes: ByteArray, routingHint: String?) {
        }

        /**
         * Called when the enclave wants to mark a given piece of mail as
         * acknowledged, so it can be deleted and should not be re-delivered.
         *
         * You should perform the acknowledgement synchronously and atomically with any posts,
         * as this is required for clients to observe transactional behaviour.
         */
        @JvmDefault
        fun acknowledgeMail(mailID: EnclaveMailId) {
        }
    }

    @Synchronized
    override fun close() {
        // Closing an unstarted or already closed EnclaveHost is allowed, because this makes it easier to use
        // Java try-with-resources and makes finally blocks more forgiving, e.g.
        //
        // try {
        //    enclave.start()
        // } finally {
        //    enclave.close()
        // }
        //
        // could yield a secondary error if an exception was thrown in enclave.start without this.
        if (hostStateManager.state !is Started) return
        try {
            enclaveHandle.destroy()
        } finally {
            hostStateManager.state = Closed
        }
    }

    private class EnclaveInfo(val enclaveImplementsEnclaveCall: Boolean, val signatureKey: PublicKey, val encryptionKey: Curve25519PublicKey)

    /** Deserializes keys and other info from the enclave during initialisation. */
    private class AdminHandler(private val host: EnclaveHost) : Handler<AdminHandler> {
        private lateinit var sender: Sender

        private var _enclaveInfo: EnclaveInfo? = null
        val enclaveInfo: EnclaveInfo get() = checkNotNull(_enclaveInfo) { "Not received enclave info" }

        override fun connect(upstream: Sender): AdminHandler = this.also { sender = upstream }

        override fun onReceive(connection: AdminHandler, input: ByteBuffer) {
            when (val type = input.get().toInt()) {
                0 -> {  // Enclave info
                    check(_enclaveInfo == null) { "Already received enclave info" }
                    val enclaveImplementsEnclaveCall = input.getBoolean()
                    val signatureKey = signatureScheme.decodePublicKey(input.getBytes(44))
                    val encryptionKey = Curve25519PublicKey(input.getBytes(32))
                    _enclaveInfo = EnclaveInfo(enclaveImplementsEnclaveCall, signatureKey, encryptionKey)
                }
                1 -> {  // AttestationResponse request
                    val ar = host._enclaveInstanceInfo!!.attestationResponse
                    val encodedCertPath = ar.certPath.encoded
                    sender.send(ar.reportBytes.intLengthPrefixSize + ar.signature.intLengthPrefixSize + encodedCertPath.size, Consumer { buffer ->
                        buffer.putIntLengthPrefixBytes(ar.reportBytes)
                        buffer.putIntLengthPrefixBytes(ar.signature)
                        buffer.put(encodedCertPath)
                    })
                }
                else -> throw IllegalStateException("Unknown type $type")
            }
        }
    }

    private class Transaction {
        val stateManager = StateManager<CallState>(Ready)
        val mailCommands = LinkedList<MailCommand>()
    }

    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private inner class EnclaveMessageHandler : Handler<EnclaveMessageHandler> {
        private lateinit var sender: Sender
        override fun connect(upstream: Sender): EnclaveMessageHandler = this.also { sender = upstream }

        private val callTypeValues = InternalCallType.values()

        @PotentialPackagePrivate("Access for EnclaveHostMockTest")
        private val threadIDToTransaction = ConcurrentHashMap<Long, Transaction>()

        override fun onReceive(connection: EnclaveMessageHandler, input: ByteBuffer) {
            val enclaveCallId = input.getLong()
            val type = callTypeValues[input.get().toInt()]
            val bytes = input.getRemainingBytes()
            val transaction = threadIDToTransaction.getValue(enclaveCallId)
            val enclaveCallStateManager = transaction.stateManager
            val intoEnclaveState = enclaveCallStateManager.checkStateIs<IntoEnclave>()
            when (type) {
                InternalCallType.CALL_RETURN -> enclaveCallStateManager.state = Response(bytes)
                InternalCallType.CALL -> {
                    requireNotNull(intoEnclaveState.callback) {
                        "Enclave responded via callUntrustedHost but a callback was not provided to callEnclave."
                    }
                    val response = intoEnclaveState.callback.invoke(bytes)
                    if (response != null) {
                        sendCallToEnclave(enclaveCallId, InternalCallType.CALL_RETURN, response)
                    }
                }
                InternalCallType.MAIL_DELIVERY -> {
                    val cmd = bytes.deserialise { MailCommand.deserialise(this) }
                    transaction.mailCommands.add(cmd)
                }
            }
        }

        fun callEnclave(bytes: ByteArray, callback: EnclaveCall?): ByteArray? {
            // To support concurrent calls into the enclave, the current thread's ID is used a call ID which is passed between
            // the host and enclave. This enables each thread to have its own state for managing the calls.
            return callEnclaveInternal(callback) { threadID ->
                sendCallToEnclave(threadID, InternalCallType.CALL, bytes)
            }
        }

        fun deliverMail(mailID: EnclaveMailId, mailBytes: ByteArray, callback: EnclaveCall?) {
            callEnclaveInternal(callback) { threadID ->
                sendMailToEnclave(threadID, mailID, mailBytes)
            }
        }

        // Sets up the state tracking and handle re-entrancy. "id" is either a call ID or a mail ID.
        private fun callEnclaveInternal(callback: EnclaveCall?, body: (id: Long) -> Unit): ByteArray? {
            val threadID = Thread.currentThread().id
            val transaction = threadIDToTransaction.computeIfAbsent(threadID) { Transaction() }
            val callStateManager = transaction.stateManager
            // It's allowed for the host to recursively call back into the enclave with callEnclave via the callback. In this
            // scenario previousCallState would represent the previous call into the enclave. Once this recursive step is
            // complete we restore the call state so that the recursion can unwind.
            val intoEnclaveState = IntoEnclave(callback)
            // We take note of the current state so that once this callEnclave has finished we revert back to it. This
            // allows nested callEnclave each with potentially their own callback.
            val previousCallState = callStateManager.transitionStateFrom<CallState>(to = intoEnclaveState)
            // Going into a callEnclave, the call state should only be Ready or IntoEnclave
            check(previousCallState !is Response)
            var response: Response? = null
            try {
                body(threadID)
            } finally {
                // We revert the state even if an exception was thrown in the callback. This enables the user to have
                // their own exception handling and reuse of the host-enclave communication channel for another call.
                if (callStateManager.state === intoEnclaveState) {
                    // If the state hasn't changed then it means the enclave didn't have a response
                    callStateManager.state = previousCallState
                } else {
                    response = callStateManager.transitionStateFrom(to = previousCallState)
                }
            }

            // If fully unwound and we have mail commands to deliver ...
            if (callStateManager.state == Ready && transaction.mailCommands.isNotEmpty()) {
                // ... the transaction ends here so pass mail commands to the host for processing.
                // If the host throws an exception, all following commands that were given are ignored.
                try {
                    val callbacks = checkNotNull(mailCallbacks) { "Enclave tried to send or acknowledge mail, but the host doesn't support that." }
                    for (cmd in transaction.mailCommands) {
                        // TODO: Is letting exceptions propagate here the right behaviour?
                        when (cmd) {
                            is MailCommand.Acknowledge -> callbacks.acknowledgeMail(cmd.id)
                            // TODO: Don't convert back and forth between byte arrays and buffers here, it's wasteful and needless.
                            is MailCommand.Post -> callbacks.postMail(cmd.bytes, cmd.routingHint)
                        }
                    }
                } finally {
                    threadIDToTransaction.remove(threadID)
                }
            }

            return response?.bytes
        }

        private fun sendCallToEnclave(threadID: Long, type: InternalCallType, bytes: ByteArray) {
            sender.send(Long.SIZE_BYTES + 1 + bytes.size, Consumer { buffer ->
                buffer.putLong(threadID)
                buffer.put(type.ordinal.toByte())
                buffer.put(bytes)
            })
        }

        private fun sendMailToEnclave(threadID: Long, mailID: Long, bytes: ByteArray) {
            sender.send(Long.SIZE_BYTES + Long.SIZE_BYTES + 1 + bytes.size, Consumer { buffer ->
                buffer.putLong(threadID)
                buffer.put(InternalCallType.MAIL_DELIVERY.ordinal.toByte())
                buffer.putLong(mailID)
                buffer.put(bytes)
            })
        }
    }

    private sealed class CallState {
        object Ready : CallState()
        class IntoEnclave(val callback: EnclaveCall?) : CallState()
        class Response(val bytes: ByteArray) : CallState()
    }

    private sealed class HostState {
        object New : HostState()
        object Started : HostState()
        object Closed : HostState()
    }
}
