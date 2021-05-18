package com.r3.conclave.host

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.MockConfiguration
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.ErrorHandler
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.common.internal.handler.SimpleMuxingHandler
import com.r3.conclave.host.EnclaveHost.CallState.*
import com.r3.conclave.host.EnclaveHost.HostState.*
import com.r3.conclave.host.internal.*
import com.r3.conclave.mail.Curve25519PublicKey
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
import java.util.function.Function

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

        /**
         * Diagnostics output outlining CPU capabilities. This is a free text field and should only be used for
         * debugging, logging. Don't try to parse the output.
         */
        @JvmStatic
        val capabilitiesDiagnostics: String
            get() = Native.getCpuCapabilitiesSummary()

        // The internal modifier has no effect on Kotlin end users because we are shading Kotlin and the metadata,
        // thus effectively converting them into Java classes. Making it synthetic hides it from the compiler.
        // TODO: Fold mock mode into the conclave-host module and make it a 'first class' mode. It will eliminate the need for this.
        @JvmSynthetic
        internal fun __internal_create(enclaveHandle: EnclaveHandle<ErrorHandler.Connection>): EnclaveHost {
            val host = EnclaveHost()
            __internal_init(host, enclaveHandle)
            return host
        }

        // The internal modifier has no effect on Kotlin end users because we are shading Kotlin and the metadata,
        // thus effectively converting them into Java classes. Making it synthetic hides it from the compiler.
        @JvmSynthetic
        internal fun __internal_init(host: EnclaveHost, enclaveHandle: EnclaveHandle<ErrorHandler.Connection>) {
            host.enclaveHandle = enclaveHandle
        }

        /**
         * Load the signed enclave for the given enclave class name.
         *
         * @param enclaveClassName The name of the enclave class to load.
         *
         * @throws IllegalArgumentException if there is no enclave file for the given class name.
         * @throws EnclaveLoadException if the enclave does not load correctly or if the platform does
         *                              not support hardware enclaves or if enclave support is disabled.
         * @throws MockOnlySupportedException if the host OS is not Linux or if the CPU doesn't support even SIMULATION
         *                                    enclaves.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class)
        fun load(enclaveClassName: String): EnclaveHost {
            return load(enclaveClassName, null)
        }

        /**
         * Load the signed enclave for the given enclave class name.
         *
         * @param enclaveClassName The name of the enclave class to load.
         * @param mockConfiguration Defines the configuration to use when loading the enclave in mock mode.
         *                          If no configuration is provided when using mock mode then a default set
         *                          of configuration parameters are used. This parameter is ignored when
         *                          not using mock mode.
         *
         * @throws IllegalArgumentException if there is no enclave file for the given class name.
         * @throws EnclaveLoadException if the enclave does not load correctly or if the platform does
         *                              not support hardware enclaves or if enclave support is disabled.
         * @throws MockOnlySupportedException if the host OS is not Linux or if the CPU doesn't support even SIMULATION
         *                                    enclaves.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class)
        fun load(enclaveClassName: String, mockConfiguration: MockConfiguration?): EnclaveHost {
            val (stream, enclaveMode) = findEnclave(enclaveClassName)

            // If this is a mock enclave then we create the host differently.
            if (enclaveMode == EnclaveMode.MOCK) {
                try {
                    val clazz = Class.forName(enclaveClassName)
                    return createMockHost(clazz, mockConfiguration)
                } catch (e: Exception) {
                    throw EnclaveLoadException("Unable to load enclave", e)
                }
            } else {
                val enclaveFile = try {
                    Files.createTempFile(enclaveClassName, "signed.so")
                } catch (e: Exception) {
                    throw EnclaveLoadException("Unable to load enclave", e)
                }
                try {
                    stream.use { Files.copy(it, enclaveFile, REPLACE_EXISTING) }
                    return createHost(enclaveMode, enclaveFile, enclaveClassName, tempFile = true)
                } catch (e: UnsatisfiedLinkError) {
                    // We get an unsatisfied link error if the native library could not be loaded on
                    // the current platform - this will happen if the user tries to load an enclave
                    // on a platform other than Linux.
                    enclaveFile.deleteQuietly()
                    throw MockOnlySupportedException("Enclaves may only be loaded on Linux hosts: ${e.message}")
                } catch (e: Exception) {
                    enclaveFile.deleteQuietly()
                    throw if (e is EnclaveLoadException) e else EnclaveLoadException("Unable to load enclave", e)
                }
            }
        }

        /**
         * Searches for an enclave. There are two places where an enclave can be found.
         * 1) In an SGX signed enclave file (.so)
         * 2) For mock enclaves, an existing class named 'className' in the classpath
         *
         * For a .so enclave file the function looks in the classpath at /package/namespace/classname-mode.signed.so.
         * For example it will look for the enclave file of "com.foo.bar.Enclave" at /com/foo/bar/Enclave-$mode.signed.so.
         *
         * For mock enclaves, the function just determines whether the class specified as 'className' exists.
         *
         * If more than one enclave is found (i.e. multiple modes, or mock + signed enclave) then an exception is thrown.
         *
         * For .so enclaves, the mode is derived from the filename but is not taken at face value. The construction of the
         * EnclaveInstanceInfoImpl in `start` makes sure the mode is correct it terms of the remote attestation.
         */
        private fun findEnclave(className: String): Pair<InputStream?, EnclaveMode> {
            // Look for an SGX enclave image.
            val found = EnclaveMode.values().mapNotNull { mode ->
                val resourceName = "/${className.replace('.', '/')}-${mode.name.toLowerCase()}.signed.so"
                val url = EnclaveHost::class.java.getResource(resourceName)
                url?.let { Pair(it, mode) }
            }
            // Also look to see if a Mock enclave object is present
            val mockEnclaveExists = try {
                Class.forName(className)
                true;
            } catch (e: ClassNotFoundException) {
                false
            }

            // Make sure we only have a single enclave image.
            if (found.isEmpty() && !mockEnclaveExists) {
                throw IllegalArgumentException(
                        """Enclave file for $className does not exist on the classpath. Please make sure the gradle dependency to the enclave project is correctly specified:
                    |    runtimeOnly project(path: ":enclave project", configuration: mode)
                    |    
                    |    where:
                    |      mode is either "release", "debug", "simulation" or "mock"
                    """.trimMargin()
                )
            } else if (found.isNotEmpty() && mockEnclaveExists) {
                throw IllegalStateException("Multiple enclave files were found: $found and mock enclave: $className")
            } else if (found.size > 1) {
                throw IllegalStateException("Multiple enclave files were found: $found")
            }

            // At this point we know we only have a single enclave present.
            if (mockEnclaveExists) {
                return Pair(null, EnclaveMode.MOCK)
            } else {
                return Pair(found[0].first.openStream(), found[0].second)
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
         * @throws EnclaveLoadException if HARDWARE enclave support is not available on the platform. The exception
         * message gives a detailed reason why HARDWARE enclaves are not supported.
         *
         * @throws MockOnlySupportedException if mock only enclave is supported on the platform. The exception message
         * gives a detailed reason why mock only enclaves are supported.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class)
        fun checkPlatformSupportsEnclaves(enableSupport: Boolean) {
            try {
                // Note the EnclaveLoadMode does not matter in this case as we are always checking the hardware
                NativeShared.checkPlatformSupportsEnclaves(enableSupport)
            } catch (e: EnclaveLoadException) {
                // Retrieve all CPU features.
                val features = NativeApi.cpuFeatures
                // Check if SSE4.1 (required even for SIMULATION) is available.
                if (!features.contains(CpuFeature.SSE4_1)) {
                    // Improve the error message, listing all available features.
                    val sb = StringBuilder()
                    sb.append(e.message)
                    sb.append(
                        features.joinToString(
                            prefix = "\nCPU features: ", separator = ", ",
                            postfix = "\nReason: SSE4.1 is required but was not found."
                        )
                    )
                    throw MockOnlySupportedException(sb.toString())
                } else {
                    throw e
                }
            } catch (e: UnsatisfiedLinkError) {
                // We get an unsatisfied link error if the native library could not be loaded on
                // the current platform - this will happen if the user tries to load an enclave
                // on a platform other than Linux.
                throw MockOnlySupportedException("Enclaves may only be loaded on Linux hosts.")
            } catch (e: Exception) {
                throw IllegalStateException("Unable to check platform support", e)
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

    private var mailCallback: Consumer<List<MailCommand>>? = null

    /**
     * The name of the sub-class of Enclave that was loaded.
     */
    val enclaveClassName: String get() = enclaveHandle.enclaveClassName

    /**
     * The mode the enclave is running in.
     */
    val enclaveMode: EnclaveMode get() = enclaveHandle.enclaveMode

    /**
     * For mock mode, the instance of the Enclave that is loaded by the host. This should be cast
     * to the type of Enclave that has been loaded and can be used to examine the state of the
     * enclave.
     *
     * For anything other than mock mode attempting to access this property will result
     * in an IllegalStateException being thrown.
     */
    val mockEnclave: Any get() = enclaveHandle.mockEnclave

    /**
     * Causes the enclave to be loaded and the `Enclave` object constructed inside.
     * This method must be called before sending is possible. Remember to call
     * [close] to free the associated enclave resources when you're done with it.
     *
     * @param attestationParameters Either an [AttestationParameters.EPID] object initialised with the required API keys,
     * or an [AttestationParameters.DCAP] object (which requires no extra parameters) when the host operating system is
     * pre-configured for DCAP attestation, typically by a cloud provider. This parameter is ignored if the enclave is
     * in mock or simulation mode and a mock attestation is used instead. Likewise, null can also be used for development
     * purposes.
     *
     * @param mailCallback A callback that will be invoked when the enclave requires the host to carry mail-related
     * actions, such as requesting delivery to the client or acknowledgement of mail. These actions, or [MailCommand]s, are
     * grouped together within the scape of a [deliverMail] or [callEnclave] call. This enables the host to action these
     * commands within the same transaction.
     *
     * If null then the enclave cannot send or acknowledge mail, although you can still deliver it.
     *
     * @throws IllegalArgumentException If the [enclaveMode] is either release or debug and no attestation parameters
     * are provided.
     * @throws IllegalStateException If the host has been closed.
     */
    @Throws(EnclaveLoadException::class)
    @Synchronized
    fun start(attestationParameters: AttestationParameters?, mailCallback: Consumer<List<MailCommand>>?) {
        start(attestationParameters, null, mailCallback)
    }

    /**
     * Causes the enclave to be loaded and the `Enclave` object constructed inside.
     * This method must be called before sending is possible. Remember to call
     * [close] to free the associated enclave resources when you're done with it.
     *
     * @param attestationParameters Either an [AttestationParameters.EPID] object initialised with the required API keys,
     * or an [AttestationParameters.DCAP] object (which requires no extra parameters) when the host operating system is
     * pre-configured for DCAP attestation, typically by a cloud provider. This parameter is ignored if the enclave is
     * in mock or simulation mode and a mock attestation is used instead. Likewise, null can also be used for development
     * purposes.
     *
     * @param mailReceipt A sealed data containing mail receipts. If the enclave is being restarted
     * and during the previous run it emitted acknowledgement receipts via [MailCommand.AcknowledgementReceipt]
     * then pass in the last receipt bytes here.
     *
     * @param mailCallback A callback that will be invoked when the enclave requires the host to carry mail-related
     * actions, such as requesting delivery to the client or acknowledgement of mail. These actions, or [MailCommand]s, are
     * grouped together within the scape of a [deliverMail] or [callEnclave] call. This enables the host to action these
     * commands within the same transaction.
     *
     * If null then the enclave cannot send or acknowledge mail, although you can still deliver it.
     *
     * @throws IllegalArgumentException If the [enclaveMode] is either release or debug and no attestation parameters
     * are provided.
     * @throws IllegalStateException If the host has been closed.
     */
    @Throws(EnclaveLoadException::class)
    @Synchronized
    fun start(attestationParameters: AttestationParameters?, mailReceipt: ByteArray?, mailCallback: Consumer<List<MailCommand>>?) {
        if (hostStateManager.state is Started) return
        hostStateManager.checkStateIsNot<Closed> { "The host has been closed." }

        // This can throw IllegalArgumentException which we don't want wrapped in a EnclaveLoadException.
        val attestationService = getAttestationService(attestationParameters)

        try {
            this.mailCallback = mailCallback
            // Set up a set of channels in and out of the enclave. Each byte array sent/received comes with
            // a prefixed channel ID that lets us split them out to separate classes.
            val mux: SimpleMuxingHandler.Connection = enclaveHandle.connection.setDownstream(SimpleMuxingHandler())
            // The admin handler deserializes keys and other info from the enclave during initialisation.
            adminHandler = mux.addDownstream(AdminHandler(this))
            // The attestation handler manages the process of generating remote attestations that are then
            // placed into the EnclaveInstanceInfo.
            val attestationConnection = mux.addDownstream(AttestationHostHandler(
                // Ignore the attestation parameters if the enclave mode is non-hardware and switch to mock attestation.
                attestationParameters?.takeIf { enclaveMode.isHardware }
            ))

            // We request an attestation at every startup. This is because in early releases the keys were ephemeral
            // and changed on restart, but this is no longer true.
            //
            // Additionally, the enclave is initialised when it receives its first bytes. We use the request for
            // the signed quote as that trigger. Therefore we know at this point adminHandler.enclaveInfo is available
            // for us to query.
            //
            // TODO: Change this to fetch quotes on demand.
            val signedQuote = attestationConnection.getSignedQuote()
            val attestation = attestationService.attestQuote(signedQuote)
            _enclaveInstanceInfo = EnclaveInstanceInfoImpl(
                adminHandler.enclaveInfo.signatureKey,
                adminHandler.enclaveInfo.encryptionKey,
                attestation
            )
            // This handler wires up callUntrustedHost -> callEnclave and mail delivery.
            enclaveMessageHandler = mux.addDownstream(EnclaveMessageHandler())
            log.debug { enclaveInstanceInfo.toString() }
            hostStateManager.state = Started

            if (mailReceipt != null)
                enclaveMessageHandler.deliverAcknowledgementReceipt(mailReceipt)

        } catch (e: Exception) {
            throw EnclaveLoadException("Unable to start enclave", e)
        }
    }

    /**
     * Return the correct attestation service for the given attestation parameters and enclave mode.
     *
     * EPID and DCAP can only be used if the enclave is release or debug, and mock is only used if the enclave is
     * simulation or mock. In the later case any attestation parameters provided are ignored.
     */
    private fun getAttestationService(attestationParameters: AttestationParameters?): AttestationService {
        fun getHardwareAttestationService(isRelease: Boolean): HardwareAttestationService {
            return when (attestationParameters) {
                is AttestationParameters.EPID -> EpidAttestationService(isRelease, attestationParameters.attestationKey)
                is AttestationParameters.DCAP -> DCAPAttestationService(isRelease)
                null -> throw IllegalArgumentException("Attestation parameters needed for $enclaveMode mode.")
            }
        }

        return when (enclaveMode) {
            EnclaveMode.RELEASE -> getHardwareAttestationService(isRelease = true)
            EnclaveMode.DEBUG -> getHardwareAttestationService(isRelease = false)
            EnclaveMode.SIMULATION -> MockAttestationService(isSimulation = true)
            EnclaveMode.MOCK -> MockAttestationService(isSimulation = false)
        }
    }

    /**
     * Provides the info of this specific loaded instance. Note that the enclave
     * instance info will remain valid across restarts of the host JVM/reloads of the
     * enclave.
     *
     * @throws IllegalStateException if the enclave has not been started.
     */
    val enclaveInstanceInfo: EnclaveInstanceInfo
        get() = checkNotNull(_enclaveInstanceInfo) { "The enclave host has not been started." }

    /**
     * Passes the given byte array to the enclave. The format of the byte
     * arrays are up to you but will typically use some sort of serialization
     * mechanism, alternatively, [DataOutputStream] is a convenient way to lay out
     * pieces of data in a fixed order.
     *
     * For this method to work the enclave class must override and implement `receiveFromUntrustedHost` The return
     * value from that method (which can be null) is returned here. It will not be received via the provided callback.
     *
     * With the provided callback the enclave also has the option of using
     * `Enclave.callUntrustedHost` and sending/receiving byte arrays in the opposite
     * direction. By chaining callbacks together, a kind of virtual stack can be constructed
     * allowing complex back-and-forth conversations between enclave and untrusted host.
     *
     * Any uncaught exceptions thrown by `receiveFromUntrustedHost` will propagate across the enclave-host boundary and
     * will be rethrown here.
     *
     * @param bytes Bytes to send to the enclave.
     * @param callback Bytes received from the enclave via `Enclave.callUntrustedHost`.
     *
     * @return The return value of the enclave's `receiveFromUntrustedHost`.
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation of `receiveFromUntrustedHost`.
     * @throws IllegalStateException If the host has not been started.
     */
    fun callEnclave(bytes: ByteArray, callback: Function<ByteArray, ByteArray?>): ByteArray? {
        return callEnclaveInternal(bytes, callback)
    }

    /**
     * Passes the given byte array to the enclave. The format of the byte
     * arrays are up to you but will typically use some sort of serialization
     * mechanism, alternatively, [DataOutputStream] is a convenient way to lay out
     * pieces of data in a fixed order.
     *
     * For this method to work the enclave class must override and implement `receiveFromUntrustedHost` The return
     * value from that method (which can be null) is returned here. It will not be received via the provided callback.
     *
     * The enclave does not have the option of using `Enclave.callUntrustedHost` for
     * sending bytes back to the host. Use the overload which takes in a callback [Function] instead.
     *
     * Any uncaught exceptions thrown by `receiveFromUntrustedHost` will propagate across the enclave-host boundary and
     * will be rethrown here.
     *
     * @param bytes Bytes to send to the enclave.
     *
     * @return The return value of the enclave's `receiveFromUntrustedHost`.
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation of `receiveFromUntrustedHost`.
     * @throws IllegalStateException If the host has not been started.
     */
    fun callEnclave(bytes: ByteArray): ByteArray? = callEnclaveInternal(bytes, null)

    private fun callEnclaveInternal(bytes: ByteArray, callback: Function<ByteArray, ByteArray?>?): ByteArray? {
        return checkStateFirst { enclaveMessageHandler.callEnclave(bytes, callback) }
    }

    /**
     * Delivers the given encrypted mail bytes to the enclave. The enclave is required to override and implement `receiveMail`
     * to receive it. If the enclave throws an exception it will be rethrown.
     * It's up to the caller to decide what to do with mails that don't seem to be
     * handled properly: discarding it and logging an error is a simple option, or
     * alternatively queuing it to disk in anticipation of a bug fix or upgrade
     * is also workable.
     *
     * It's possible the callback provided to [start] will receive a [MailCommand.PostMail] or [MailCommand.AcknowledgeMail]
     * on the same thread, requesting mail to be sent back in response and/or acknowledgement, respectively. However, it's
     * also possible the enclave will hold the mail without requesting any action.
     *
     * When an enclave is started, you must redeliver, in order, any unacknowledged
     * mail so the enclave can rebuild its internal state.
     *
     * @param id An identifier that will be used to identify acknowledged mail via [MailCommand.AcknowledgeMail]. The
     * scope of this ID is up until the enclave acknowledges the mail, so it doesn't have to be fully unique forever, nor
     * does it need to be derived from anything in particular. A good choice is a row ID in a database or a queue message
     * ID, for example.
     * @param mail The encrypted mail received from a remote client.
     * @param routingHint An arbitrary bit of data identifying the sender on the host side. The enclave can pass this
     * back through to [MailCommand.PostMail] to ask the host to deliver the reply to the right location.
     * @param callback If the enclave calls `Enclave.callUntrustedHost` then the
     * bytes will be passed to this object for consumption and generation of the
     * response.
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation for `receiveMail`.
     * @throws IllegalStateException If the host has not been started.
     */
    fun deliverMail(id: Long, mail: ByteArray, routingHint: String?, callback: Function<ByteArray, ByteArray?>) {
        deliverMailInternal(id, mail, routingHint, callback)
    }

    /**
     * Delivers the given encrypted mail bytes to the enclave. The enclave is required to override and implement `receiveMail`
     * to receive it. If the enclave throws an exception it will be rethrown.
     * It's up to the caller to decide what to do with mails that don't seem to be
     * handled properly: discarding it and logging an error is a simple option, or
     * alternatively queuing it to disk in anticipation of a bug fix or upgrade
     * is also workable.
     *
     * It's possible the callback provided to [start] will receive a [MailCommand.PostMail] or [MailCommand.AcknowledgeMail]
     * on the same thread, requesting mail to be sent back in response and/or acknowledgement, respectively. However, it's
     * also possible the enclave will hold the mail without requesting any action.
     *
     * When an enclave is started, you must redeliver, in order, any unacknowledged
     * mail so the enclave can rebuild its internal state.
     *
     * Note: The enclave does not have the option of using `Enclave.callUntrustedHost` for
     * sending bytes back to the host. Use the overload which takes in a callback [Function] instead.
     *
     * @param id an identifier that will be used to identify acknowledged mail via [MailCommand.AcknowledgeMail]. The
     * scope of this ID is up until the enclave acknowledges the mail, so it doesn't have to be fully unique forever, nor
     * does it need to be derived from anything in particular. A good choice is a row ID in a database or a queue message
     * ID, for example.
     * @param mail the encrypted mail received from a remote client.
     * @param routingHint An arbitrary bit of data identifying the sender on the host side. The enclave can pass this
     * back through to [MailCommand.PostMail] to ask the host to deliver the reply to the right location.
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation for `receiveMail`.
     * @throws IllegalStateException If the host has not been started.
     */
    fun deliverMail(id: Long, mail: ByteArray, routingHint: String?) {
        deliverMailInternal(id, mail, routingHint, null)
    }

    private fun deliverMailInternal(
        id: Long,
        mail: ByteArray,
        routingHint: String?,
        callback: Function<ByteArray, ByteArray?>?
    ) {
        return checkStateFirst { enclaveMessageHandler.deliverMail(id, mail, callback, routingHint) }
    }

    private inline fun <T> checkStateFirst(block: () -> T): T {
        return when (hostStateManager.state) {
            New -> throw IllegalStateException("The enclave host has not been started.")
            Closed -> throw IllegalStateException("The enclave host has been closed.")
            Started -> block()
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

    private class EnclaveInfo(val signatureKey: PublicKey, val encryptionKey: Curve25519PublicKey)

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
                    val signatureKey = signatureScheme.decodePublicKey(input.getBytes(44))
                    val encryptionKey = Curve25519PublicKey(input.getBytes(32))
                    _enclaveInfo = EnclaveInfo(signatureKey, encryptionKey)
                }
                1 -> {  // Attestation request
                    val attestationBytes = writeData { host._enclaveInstanceInfo!!.attestation.writeTo(this) }
                    sender.send(attestationBytes.size) { buffer ->
                        buffer.put(attestationBytes)
                    }
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
        private val mailCommandTypeValues = MailCommandType.values()

        @PotentialPackagePrivate("Access for EnclaveHostMockTest")
        private val threadIDToTransaction = ConcurrentHashMap<Long, Transaction>()

        override fun onReceive(connection: EnclaveMessageHandler, input: ByteBuffer) {
            val threadID = input.getLong()
            val callType = callTypeValues[input.get().toInt()]
            val transaction = threadIDToTransaction.getValue(threadID)
            val enclaveCallStateManager = transaction.stateManager
            val intoEnclaveState = enclaveCallStateManager.checkStateIs<IntoEnclave>()
            when (callType) {
                InternalCallType.CALL_RETURN -> enclaveCallStateManager.state = Response(input.getRemainingBytes())
                InternalCallType.CALL -> {
                    val bytes = input.getRemainingBytes()
                    requireNotNull(intoEnclaveState.callback) {
                        "Enclave responded via callUntrustedHost but a callback was not provided to callEnclave."
                    }
                    val response = intoEnclaveState.callback.apply(bytes)
                    if (response != null) {
                        sendCallToEnclave(threadID, InternalCallType.CALL_RETURN, response)
                    }
                }
                InternalCallType.MAIL_DELIVERY -> {
                    val cmd = when (mailCommandTypeValues[input.get().toInt()]) {
                        MailCommandType.POST -> {
                            // routingHint can be null/missing.
                            val routingHint = String(input.getIntLengthPrefixBytes()).takeIf { it.isNotBlank() }
                            // rest of the body to deliver (should be encrypted).
                            val encryptedBytes = input.getRemainingBytes()
                            MailCommand.PostMail(encryptedBytes, routingHint)
                        }
                        MailCommandType.ACKNOWLEDGE -> {
                            MailCommand.AcknowledgeMail(input.getLong())
                        }
                        MailCommandType.RECEIPT -> {
                            val sealed = input.getIntLengthPrefixBytes()
                            MailCommand.AcknowledgementReceipt(sealed)
                        }
                    }
                    transaction.mailCommands.add(cmd)
                }
                else -> {}
            }
        }

        fun callEnclave(bytes: ByteArray, callback: Function<ByteArray, ByteArray?>?): ByteArray? {
            // To support concurrent calls into the enclave, the current thread's ID is used a call ID which is passed between
            // the host and enclave. This enables each thread to have its own state for managing the calls.
            return callEnclaveInternal(callback) { threadID ->
                sendCallToEnclave(threadID, InternalCallType.CALL, bytes)
            }
        }

        fun deliverMail(
            mailID: Long,
            mailBytes: ByteArray,
            callback: Function<ByteArray, ByteArray?>?,
            routingHint: String?
        ) {
            callEnclaveInternal(callback) { threadID ->
                sendMailToEnclave(threadID, mailID, mailBytes, routingHint)
            }
        }

        fun deliverAcknowledgementReceipt(
            receipts: ByteArray
        ) {
            sendReceiptToEnclave(receipts)
        }

        // Sets up the state tracking and handle re-entrancy. "id" is either a call ID or a mail ID.
        private fun callEnclaveInternal(
            callback: Function<ByteArray, ByteArray?>?,
            body: (id: Long) -> Unit
        ): ByteArray? {
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
            // Going into a callEnclave, the call state should only be Ready or IntoEnclave.
            check(previousCallState !is Response)
            var response: Response? = null
            try {
                body(threadID)
            } finally {
                // We revert the state even if an exception was thrown in the callback. This enables the user to have
                // their own exception handling and reuse of the host-enclave communication channel for another call.
                if (callStateManager.state === intoEnclaveState) {
                    // If the state hasn't changed then it means the enclave didn't have a response.
                    callStateManager.state = previousCallState
                } else {
                    response = callStateManager.transitionStateFrom(to = previousCallState)
                }
            }

            // If fully unwound and we have mail commands to deliver ...
            if (callStateManager.state == Ready && transaction.mailCommands.isNotEmpty()) {
                // ... the transaction ends here so pass mail commands to the host for processing.
                try {
                    val mailCallback = checkNotNull(mailCallback) {
                        "Enclave tried to send or acknowledge mail, but the host doesn't support that."
                    }
                    mailCallback.accept(transaction.mailCommands)
                } finally {
                    threadIDToTransaction.remove(threadID)
                }
            }

            return response?.bytes
        }

        private fun sendCallToEnclave(threadID: Long, type: InternalCallType, bytes: ByteArray) {
            sender.send(Long.SIZE_BYTES + 1 + bytes.size) { buffer ->
                buffer.putLong(threadID)
                buffer.put(type.ordinal.toByte())
                buffer.put(bytes)
            }
        }

        private fun sendMailToEnclave(threadID: Long, mailID: Long, mailBytes: ByteArray, routingHint: String?) {
            val routingHintBytes = routingHint?.toByteArray()
            sender.send(
                Long.SIZE_BYTES + Long.SIZE_BYTES + 1 + mailBytes.size + 4 + (routingHintBytes?.size ?: 0)
            ) { buffer ->
                buffer.putLong(threadID)
                buffer.put(InternalCallType.MAIL_DELIVERY.ordinal.toByte())
                buffer.putLong(mailID)
                if (routingHintBytes != null) {
                    buffer.putIntLengthPrefixBytes(routingHintBytes)
                } else
                    buffer.putInt(0)
                buffer.put(mailBytes)
            }
        }

        private fun sendReceiptToEnclave(receipt: ByteArray) {
            sender.send(
                Long.SIZE_BYTES + 1 + Int.SIZE_BYTES + receipt.size
            ) { buffer ->
                buffer.putLong(Thread.currentThread().id) // not really used, but is expected on Enclave side
                buffer.put(InternalCallType.ACKNOWLEDGEMENT_RECEIPT.ordinal.toByte())
                buffer.putIntLengthPrefixBytes(receipt)
            }
        }
    }

    private sealed class CallState {
        object Ready : CallState()
        class IntoEnclave(val callback: Function<ByteArray, ByteArray?>?) : CallState()
        class Response(val bytes: ByteArray) : CallState()
    }

    private sealed class HostState {
        object New : HostState()
        object Started : HostState()
        object Closed : HostState()
    }
}
