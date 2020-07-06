package com.r3.conclave.host

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.ErrorHandler
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.common.internal.handler.SimpleMuxingHandler
import com.r3.conclave.host.EnclaveHost.EnclaveCallHandler.CallState.*
import com.r3.conclave.host.EnclaveHost.HostState.*
import com.r3.conclave.host.internal.*
import com.r3.conclave.utilities.internal.getBoolean
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.putBoolean
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.PublicKey
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
         * in start makes sure the mode is correct it terms of the remote attestation.
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
            } catch (e: Exception) {
                throw EnclaveLoadException("Unable to check platform support", e)
            }
        }
    }

    // EnclaveHandle is an internal class and thus to prevent it from leaking into the public API this is not a c'tor parameter.
    private lateinit var enclaveHandle: EnclaveHandle<ErrorHandler.Connection>
    private val hostStateManager = StateManager<HostState>(New)
    // This is only initialised if the enclave is able to receive calls from the host (i.e. implements EnclaveCall)
    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private var enclaveCallHandler: EnclaveCallHandler? = null
    private var _enclaveInstanceInfo: EnclaveInstanceInfo? = null

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
     * Note: This parameter is temporary and will be removed in future version.
     *
     * @param attestationKey The private attestation key needed to access the attestation service. Please see
     * https://api.portal.trustedservices.intel.com/EPID-attestation for further details on how obtain one.
     *
     * This parameter is not used if the enclave is in simulation mode (as no attestation is done in simulation) and null
     * can be provided.
     */
    @Throws(EnclaveLoadException::class)
    @Synchronized
    // TODO MailHandler parameter
    fun start(spid: OpaqueBytes?, attestationKey: String?) {
        if (hostStateManager.state is Started) return
        hostStateManager.checkStateIsNot<Closed> { "The host has been closed." }
        require(spid == null || spid.size == 16) { "Invalid SPID length" }
        try {
            val mux = enclaveHandle.connection.setDownstream(SimpleMuxingHandler())
            val adminHandler = mux.addDownstream(AdminHandler())
            val attestationConnection = mux.addDownstream(
                    EpidAttestationHostHandler(
                            SgxQuoteType.LINKABLE,
                            spid?.let { Cursor(SgxSpid, it.buffer()) } ?: Cursor.allocate(SgxSpid),
                            enclaveMode == EnclaveMode.MOCK
                    )
            )
            val signedQuote = attestationConnection.getSignedQuote()
            // The enclave is initialised when it receives its first bytes. We use the request for the signed quote as
            // that trigger. Therefore we know at this point adminHandler.enclaveInfo is available for us to query.
            if (adminHandler.enclaveInfo.enclaveImplementsEnclaveCall) {
                enclaveCallHandler = mux.addDownstream(EnclaveCallHandler())
            }
            _enclaveInstanceInfo = getAttestationService(attestationKey).doAttest(
                    adminHandler.enclaveInfo.signatureKey,
                    signedQuote,
                    enclaveMode
            )
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

    private fun callEnclaveInternal(bytes: ByteArray, callback: EnclaveCall?) : ByteArray? {
        val enclaveCallHandler = when (hostStateManager.state) {
            New -> throw IllegalStateException("The host has not been started.")
            Started -> checkNotNull(this.enclaveCallHandler) {
                "The enclave does not implement EnclaveCall to receive messages from the host."
            }
            Closed -> throw IllegalStateException("The host has been closed.")
        }
        return enclaveCallHandler.callEnclave(bytes, callback)
    }

    // TODO deliverMail

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

    private class EnclaveInfo(val enclaveImplementsEnclaveCall: Boolean, val signatureKey: PublicKey)

    private class AdminHandler : Handler<AdminHandler> {
        private var _enclaveInfo: EnclaveInfo? = null
        val enclaveInfo: EnclaveInfo get() = checkNotNull(_enclaveInfo) { "Not received enclave info" }

        override fun connect(upstream: Sender): AdminHandler = this

        override fun onReceive(connection: AdminHandler, input: ByteBuffer) {
            check(_enclaveInfo == null) { "Already received enclave info" }
            val enclaveImplementsEnclaveCall = input.getBoolean()
            val signatureKey = signatureScheme.decodePublicKey(input.getRemainingBytes())
            _enclaveInfo = EnclaveInfo(enclaveImplementsEnclaveCall, signatureKey)
        }
    }

    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private class EnclaveCallHandler : Handler<EnclaveCallHandler> {
        @PotentialPackagePrivate("Access for EnclaveHostMockTest")
        private val enclaveCalls = ConcurrentHashMap<Long, StateManager<CallState>>()
        private lateinit var sender: Sender

        override fun connect(upstream: Sender): EnclaveCallHandler {
            sender = upstream
            return this
        }

        override fun onReceive(connection: EnclaveCallHandler, input: ByteBuffer) {
            val enclaveCallId = input.getLong()
            val isEnclaveCallReturn = input.getBoolean()
            val bytes = input.getRemainingBytes()
            val enclaveCallStateManager = enclaveCalls.getValue(enclaveCallId)
            val intoEnclaveState = enclaveCallStateManager.checkStateIs<IntoEnclave>()
            if (isEnclaveCallReturn) {
                enclaveCallStateManager.state = Response(bytes)
            } else {
                requireNotNull(intoEnclaveState.callback) {
                    "Enclave responded via callUntrustedHost but a callback was not provided to callEnclave."
                }
                val response = intoEnclaveState.callback.invoke(bytes)
                if (response != null) {
                    sendToEnclave(enclaveCallId, response, isEnclaveCallReturn = true)
                }
            }
        }

        fun callEnclave(bytes: ByteArray, callback: EnclaveCall?) : ByteArray? {
            // To support concurrent calls into the enclave, the current thread's ID is used a call ID which is passed between
            // the host and enclave. This enables each thread to have its own state for managing the calls.
            val enclaveCallId = Thread.currentThread().id
            val callStateManager = enclaveCalls.computeIfAbsent(enclaveCallId) { StateManager(Ready) }
            // It's allowed for the host to recursively call back into the enclave with callEnclave via the callback. In this
            // scenario previousCallState would represent the previous call into the enclave. Once this recusive step  is
            // complete we restore the call state so that the recursion can unwind.
            val intoEnclaveState = IntoEnclave(callback)
            // We take note of the current state so that once this callEnclave has finished we revert back to it. This
            // allows nested callEnclave each with potentially their own callback.
            val previousCallState = callStateManager.transitionStateFrom<CallState>(to = intoEnclaveState)
            // Going into a callEnclave, the call state should only be Ready or IntoEnclave
            check(previousCallState !is Response)
            var response: Response? = null
            try {
                sendToEnclave(enclaveCallId, bytes, isEnclaveCallReturn = false)
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
            return response?.bytes
        }

        private fun sendToEnclave(enclaveCallId: Long, bytes: ByteArray, isEnclaveCallReturn: Boolean) {
            sender.send(Long.SIZE_BYTES + 1 + bytes.size, Consumer { buffer ->
                buffer.putLong(enclaveCallId)
                buffer.putBoolean(isEnclaveCallReturn)
                buffer.put(bytes)
            })
        }

        private sealed class CallState {
            object Ready : CallState()
            class IntoEnclave(val callback: EnclaveCall?) : CallState()
            class Response(val bytes: ByteArray) : CallState()
        }
    }

    private sealed class HostState {
        object New : HostState()
        object Started : HostState()
        object Closed : HostState()
    }
}
