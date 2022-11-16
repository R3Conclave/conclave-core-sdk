package com.r3.conclave.host

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.r3.conclave.common.*
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.InternalCallType.*
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.common.internal.kds.KDSErrorResponse
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.host.EnclaveHost.CallState.*
import com.r3.conclave.host.EnclaveHost.HostState.*
import com.r3.conclave.host.internal.*
import com.r3.conclave.host.internal.EnclaveScanner.ScanResult
import com.r3.conclave.host.internal.attestation.AttestationService
import com.r3.conclave.host.internal.attestation.AttestationServiceFactory
import com.r3.conclave.host.internal.attestation.EnclaveQuoteService
import com.r3.conclave.host.internal.attestation.EnclaveQuoteServiceFactory
import com.r3.conclave.host.internal.fatfs.FileSystemHandler
import com.r3.conclave.host.internal.gramine.GramineEnclaveHandle
import com.r3.conclave.host.internal.kds.KDSPrivateKeyRequest
import com.r3.conclave.host.internal.kds.KDSPrivateKeyResponse
import com.r3.conclave.host.kds.KDSConfiguration
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.*
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
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
class EnclaveHost private constructor(
    private val enclaveHandle: EnclaveHandle
) : AutoCloseable {
    /**
     * Suppress kotlin specific companion objects from our API documentation.
     * The public items within the object are still published in the documentation.
     * @suppress
     */
    companion object {
        private val log = loggerFor<EnclaveHost>()
        private val signatureScheme = SignatureSchemeEdDSA()
        private val jsonMapper = JsonMapper.builder().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS).build()

        /**
         * Diagnostics output outlining CPU capabilities. This is a free text field and should only be used for
         * debugging, logging. Don't try to parse the output.
         */
        @JvmStatic
        val capabilitiesDiagnostics: String
            get() = Native.getCpuCapabilitiesSummary()

        /**
         * Load the signed enclave for the given enclave class name.
         *
         * @param enclaveClassName The name of the enclave class to load.
         *
         * @throws IllegalArgumentException if there is no enclave file for the given class name.
         * @throws IllegalStateException if more than one enclave file is found.
         * @throws EnclaveLoadException if the enclave does not load correctly or if the platform does
         *                              not support hardware enclaves or if enclave support is disabled.
         * @throws PlatformSupportException if the mode is not mock and the host OS is not Linux or if the CPU doesn't
         *                                  support SGX enclave in simulation mode or higher.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class, PlatformSupportException::class)
        fun load(enclaveClassName: String): EnclaveHost = load(enclaveClassName, null)

        /**
         * Scan the classpath and load the single signed enclave that is found.
         *
         * @throws IllegalStateException if no enclave file is found or more than one enclave file is found.
         * @throws EnclaveLoadException if the enclave does not load correctly or if the platform does
         *                              not support hardware enclaves or if enclave support is disabled.
         * @throws PlatformSupportException if the mode is not mock and the host OS is not Linux or if the CPU doesn't
         *                                  support SGX enclave in simulation mode or higher.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class, PlatformSupportException::class)
        fun load(): EnclaveHost = load(null)

        /**
         * Load the signed enclave for the given enclave class name.
         *
         * @param enclaveClassName The name of the enclave class to load.
         * @param mockConfiguration Defines the configuration to use when loading the enclave in mock mode.
         *                          If no configuration is provided when using mock mode then a default set
         *                          of configuration parameters are used. This parameter is ignored when
         *                          not using mock mode.
         *
         * @throws IllegalArgumentException if there is no enclave file for the given class name or if
         *                                  an unexpected error occurs when trying to check platform support.
         * @throws IllegalStateException if more than one enclave file is found.
         * @throws EnclaveLoadException if the enclave does not load correctly or if the platform does
         *                              not support enclaves in the required mode.
         * @throws PlatformSupportException if the mode is not mock and the host OS is not Linux or if the CPU doesn't
         *                                  support SGX enclave in simulation mode or higher.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class, PlatformSupportException::class)
        fun load(enclaveClassName: String, mockConfiguration: MockConfiguration?): EnclaveHost {
            return createEnclaveHost(EnclaveScanner().findEnclave(enclaveClassName), mockConfiguration)
        }

        /**
         * Scan the classpath and load the single signed enclave that is found.
         *
         * @param mockConfiguration Defines the configuration to use when loading the enclave in mock mode.
         *                          If no configuration is provided when using mock mode then a default set
         *                          of configuration parameters are used. This parameter is ignored when
         *                          not using mock mode.
         *
         * @throws IllegalStateException if no enclave file is found or if more than one enclave file is found.
         * @throws EnclaveLoadException if the enclave does not load correctly or if the platform does
         *                              not support enclaves in the required mode.
         * @throws PlatformSupportException if the mode is not mock and the host OS is not Linux or if the CPU doesn't
         *                                  support SGX enclave in simulation mode or higher.
         */
        @JvmStatic
        @Throws(EnclaveLoadException::class, PlatformSupportException::class)
        fun load(mockConfiguration: MockConfiguration?): EnclaveHost {
            return createEnclaveHost(EnclaveScanner().findEnclave(), mockConfiguration)
        }

        /**
         * Determine whether simulated enclaves are supported on the current platform. Irrespective of the
         * current project mode. To support simulated enclaves, the platform needs to be Linux.
         *
         * @return Boolean true if simulated enclaves are supported, false otherwise.
         */
        @JvmStatic
        fun isSimulatedEnclaveSupported(): Boolean = isNativeEnclaveSupported(requireHardwareSupport = false)

        /**
         * Determine whether hardware enclaves are supported on the current platform. Irrespective of the
         * current project mode. To support hardware enclaves, the platform needs to be Linux and the system
         * needs a CPU which supports Intel SGX.
         *
         * @return Boolean true if hardware enclaves are supported, false otherwise.
         */
        @JvmStatic
        fun isHardwareEnclaveSupported(): Boolean = isNativeEnclaveSupported(requireHardwareSupport = true)

        /**
         * Determine which enclave modes are supported on the current platform. Irrespective of the current
         * project mode.
         *
         * @return Set<EnclaveMode> containing the set of enclave modes that are supported by the current platform.
         */
        @JvmStatic
        fun getSupportedModes(): Set<EnclaveMode> {
            val supportedModes = EnumSet.of(EnclaveMode.MOCK)
            if (isSimulatedEnclaveSupported()) {
                supportedModes.add(EnclaveMode.SIMULATION)
                if (isHardwareEnclaveSupported()) {
                    supportedModes.add(EnclaveMode.DEBUG)
                    supportedModes.add(EnclaveMode.RELEASE)
                }
            }
            return supportedModes
        }

        /**
         * Some platforms support software enablement of SGX, this method will attempt to do this. This may
         * require elevated privileges and/or a reboot in order to work. The method is safe to call on machines
         * where SGX is already enabled.
         *
         * @throws PlatformSupportException If SGX could not be enabled.
         */
        @JvmStatic
        @Throws(PlatformSupportException::class)
        fun enableHardwareEnclaveSupport() {
            checkNotLinux()
            NativeShared.enablePlatformHardwareEnclaveSupport()
        }

        // The internal modifier prevents this from appearing in the API docs, however because we shade Kotlin it will
        // still be available to Java users. We solve that by making it synthetic which hides it from the Java compiler.
        @JvmSynthetic
        @JvmStatic
        internal fun internalCreateNonMock(scanResult: ScanResult): EnclaveHost {
            val enclaveHandle = when (scanResult) {
                is ScanResult.GraalVM -> NativeEnclaveHandle(
                    scanResult.enclaveMode,
                    scanResult.enclaveClassName,
                    scanResult.soFileUrl
                )
                is ScanResult.Gramine -> GramineEnclaveHandle(
                    scanResult.enclaveMode,
                    scanResult.enclaveClassName,
                    scanResult.zipFileUrl
                )
                is ScanResult.Mock -> throw IllegalArgumentException()
            }
            return EnclaveHost(enclaveHandle)
        }

        // The internal modifier prevents this from appearing in the API docs, however because we shade Kotlin it will
        // still be available to Java users. We solve that by making it synthetic which hides it from the Java compiler.
        @JvmSynthetic
        @JvmStatic
        internal fun internalCreateMock(
            enclaveClass: Class<*>,
            mockConfiguration: MockConfiguration? = null,
            kdsConfig: EnclaveKdsConfig? = null
        ): EnclaveHost {
            // For mock mode ensure the host can access the enclave constructor. It may have been set as private.
            val constructor = enclaveClass.getDeclaredConstructor().apply { isAccessible = true }
            val enclaveHandle = MockEnclaveHandle(
                constructor.newInstance(),
                mockConfiguration,
                kdsConfig
            )
            return EnclaveHost(enclaveHandle)
        }

        private fun createEnclaveHost(result: ScanResult, mockConfiguration: MockConfiguration?): EnclaveHost {
            try {
                return when (result) {
                    is ScanResult.Mock -> {
                        // Here we do not call checkPlatformEnclaveSupport as mock mode is supported on any platform
                        val enclaveClass = Class.forName(result.enclaveClassName)
                        internalCreateMock(enclaveClass, mockConfiguration)
                    }
                    else -> {
                        checkPlatformEnclaveSupport(result.enclaveMode)
                        internalCreateNonMock(result)
                    }
                }
            } catch (e: EnclaveLoadException) {
                throw e
            } catch (e: Exception) {
                throw EnclaveLoadException("Unable to load enclave", e)
            }
        }

        private fun isNativeEnclaveSupported(requireHardwareSupport: Boolean): Boolean {
            if (!UtilsOS.isLinux()) {
                return false
            }
            return try {
                NativeShared.checkPlatformEnclaveSupport(requireHardwareSupport)
                true
            } catch (e: PlatformSupportException) {
                false
            }
        }

        /**
         * Checks to see if the platform supports enclaves in a given mode and throws an exception if not.
         *
         * @throws PlatformSupportException if the requested mode is not supported on the system.
         */
        private fun checkPlatformEnclaveSupport(enclaveMode: EnclaveMode) {
            // All platforms support MOCK mode
            if (enclaveMode == EnclaveMode.MOCK)
                return

            checkNotLinux()

            val requireHardwareSupport = enclaveMode.isHardware
            try {
                NativeShared.checkPlatformEnclaveSupport(requireHardwareSupport)
            } catch (e: PlatformSupportException) {
                // Improve error message in case that SSE4.1 is missing
                val features = NativeApi.cpuFeatures
                if (!features.contains(CpuFeature.SSE4_1)) {
                    val sb = StringBuilder()
                    sb.append(e.message)
                    sb.append(
                        features.joinToString(
                            prefix = "\nCPU features: ", separator = ", ",
                            postfix = "\nReason: SSE4.1 is required but was not found."
                        )
                    )
                    throw PlatformSupportException(sb.toString(), e)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                throw IllegalStateException("Unable to check platform support", e)
            }
        }

        private fun checkNotLinux() {
            if (!UtilsOS.isLinux()) {
                val message =
                    "This system does not support hardware enclaves. " +
                            "If you wish to run enclaves built in simulation, release or debug mode, " +
                            "you must run in a linux environment. Consult the conclave documentation " +
                            "for platform specific instructions."
                throw PlatformSupportException(message)
            }
        }
    }

    private var kdsConfiguration: KDSConfiguration? = null
    private var fileSystemHandler: FileSystemHandler? = null

    private val hostStateManager = StateManager<HostState>(New)
    private val setEnclaveInfoCallHandler = SetEnclaveInfoCallHandler()

    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private val enclaveMessageHandler = EnclaveMessageHandler()
    private var _enclaveInstanceInfo: EnclaveInstanceInfoImpl? = null

    private lateinit var commandsCallback: Consumer<List<MailCommand>>

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

    private lateinit var attestationService: AttestationService
    private lateinit var quotingService: EnclaveQuoteService

    @Throws(EnclaveLoadException::class)
    @Synchronized
    fun start(
        attestationParameters: AttestationParameters?,
        sealedState: ByteArray?,
        enclaveFileSystemFile: Path?,
        commandsCallback: Consumer<List<MailCommand>>
    ) {
        start(attestationParameters, sealedState, enclaveFileSystemFile, null, commandsCallback)
    }

    /**
     * Causes the enclave to be loaded and the [com.r3.conclave.enclave.Enclave] object constructed inside.
     * This method must be called before sending is possible. Remember to call
     * [close] to free the associated enclave resources when you're done with it.
     *
     * @param attestationParameters Either an [AttestationParameters.EPID] object initialised with the required API keys,
     * or an [AttestationParameters.DCAP] object (which requires no extra parameters) when the host operating system is
     * pre-configured for DCAP attestation, typically by a cloud provider. This parameter is ignored if the enclave is
     * in mock or simulation mode and a mock attestation is used instead. Likewise, null can also be used for development
     * purposes.
     *
     * @param sealedState The last sealed state that was emitted by the enclave via [MailCommand.StoreSealedState]. The
     * sealed state is an encrypted blob of the enclave's internal state and it's updated by the enclave as it processes
     * mail (the contents of [com.r3.conclave.enclave.Enclave.persistentMap] is also part of this state). Each new sealed state that is
     * emitted via [MailCommand.StoreSealedState] supercedes the previous one and must be securely persisted. Failure
     * to do this will result in the enclave's clients detecting a "rollback" attack if the enclave is restarted.
     * Typically the sealed state should be stored in a database, inside the same database transaction that
     * processes thhe other mail commands, such as [MailCommand.PostMail]. More information can be found
     * [here](https://docs.conclave.net/persistence.html).
     *
     * @param enclaveFileSystemFile File where the enclave's encrypted file system will be persisted to. This can be null
     * if the enclave's configured to use one. If it is then a file path must be provided. More information can be found
     * [here](https://docs.conclave.net/persistence.html).
     *
     * @param kdsConfiguration Configuration for connecting to a key derivation service (KDS) in case the enclave needs
     * to use one for encrypting persisted data. More information can be found [here](https://docs.conclave.net/kds-configuration.html).
     *
     * @param commandsCallback A callback that is automatically invoked after the end of every [callEnclave] and
     * [deliverMail] call. The callback returns a list of actions, or [MailCommand]s, which need to be actioned together,
     * ideally within the scope single transaction.
     *
     * The callback is invoked serially, never concurrently, and in the order that they need to be actioned. This
     * means there's no need to do any external synchronization.
     *
     * @throws IllegalArgumentException If the [enclaveMode] is either release or debug and no attestation parameters
     * are provided.
     * @throws EnclaveLoadException If the enclave could not be started.
     * @throws IllegalStateException If the host has been closed.
     */
    @Throws(EnclaveLoadException::class)
    @Synchronized
    fun start(
        attestationParameters: AttestationParameters?,
        sealedState: ByteArray?,
        enclaveFileSystemFile: Path?,
        kdsConfiguration: KDSConfiguration?,
        commandsCallback: Consumer<List<MailCommand>>
    ) {
        if (hostStateManager.state is Started) return
        hostStateManager.checkStateIsNot<Closed> { "The host has been closed." }

        // This can throw IllegalArgumentException which we don't want wrapped in a EnclaveLoadException.
        attestationService = AttestationServiceFactory.getService(enclaveMode, attestationParameters, enclaveHandle)

        quotingService =
            EnclaveQuoteServiceFactory.getService(attestationParameters?.takeIf { enclaveMode.isHardware }, enclaveHandle)

        try {
            this.commandsCallback = commandsCallback

            // Register call handlers
            enclaveHandle.enclaveInterface.apply {
                registerCallHandler(HostCallType.GET_QUOTING_ENCLAVE_INFO, GetQuotingEnclaveInfoHandler())
                registerCallHandler(HostCallType.GET_SIGNED_QUOTE, GetSignedQuoteHandler())
                registerCallHandler(HostCallType.GET_ATTESTATION, GetAttestationHandler())
                registerCallHandler(HostCallType.SET_ENCLAVE_INFO, setEnclaveInfoCallHandler)
                registerCallHandler(HostCallType.CALL_MESSAGE_HANDLER, enclaveMessageHandler)
            }

            // Initialise the enclave before fetching enclave instance info
            enclaveHandle.initialise()
            updateAttestation()
            log.debug { enclaveInstanceInfo.toString() }

            // Once the EnclaveInstanceInfo has been updated, we can do a KDS request for the persistence key.
            if (kdsConfiguration != null) {
                this.kdsConfiguration = kdsConfiguration
                // TODO We can avoid this ECALL if we get the enclave to send its persistence key spec when it's
                //  first initialised.
                val persistenceKeySpec = enclaveHandle.enclaveInterface.getKdsPersistenceKeySpec()
                //  If the enclave is configured also with KDS spec for persistence, we trigger the private key request.
                //    Note that the kdsConfiguration is also used in the context of KdsPostOffice
                if (persistenceKeySpec != null) {
                    val kdsResponse = executeKdsPrivateKeyRequest(persistenceKeySpec, kdsConfiguration)
                    enclaveHandle.enclaveInterface.setKdsPersistenceKey(kdsResponse)
                }
            }

            if (enclaveFileSystemFile != null) {
                log.info("Setting up persistent enclave file system...")
            }
            fileSystemHandler = prepareFileSystemHandler(enclaveFileSystemFile)
            enclaveHandle.enclaveInterface.startEnclave(sealedState)
            if (enclaveFileSystemFile != null) {
                log.info("Setup of the file system completed successfully.")
            }

            hostStateManager.state = Started
        } catch (e: Exception) {
            throw EnclaveLoadException("Unable to start enclave", e)
        }
    }

    private fun prepareFileSystemHandler(enclaveFileSystemFile: Path?): FileSystemHandler? {
        return if (isFileSystemSupported()) {
            val fileSystemFilePaths = if (enclaveFileSystemFile != null) listOf(enclaveFileSystemFile) else emptyList()
            FileSystemHandler(fileSystemFilePaths, enclaveMode)
        } else {
            null
        }
    }

    private fun isFileSystemSupported(): Boolean {
        //  This filesystem implementation is only supported in SIMULATION, DEBUG, RELEASE mode in Graal VM mode only
        // TODO: Fix this once we have filesystem supported in Gramine
        return enclaveMode != EnclaveMode.MOCK && enclaveHandle is NativeEnclaveHandle
    }

    private fun executeKdsPrivateKeyRequest(
        keySpec: KDSKeySpec,
        kdsConfiguration: KDSConfiguration
    ): KDSPrivateKeyResponse {
        val url = URL("${kdsConfiguration.url}/private")
        val con: HttpURLConnection = url.openConnection() as HttpURLConnection
        con.connectTimeout = kdsConfiguration.timeout.toMillis().toInt()
        con.readTimeout = kdsConfiguration.timeout.toMillis().toInt()
        con.requestMethod = "POST"
        con.setRequestProperty("Content-Type", "application/json; utf-8")
        con.setRequestProperty("API-VERSION", "1")
        con.doOutput = true

        val kdsPrivateKeyRequest = KDSPrivateKeyRequest(
            // TODO Cache the serialised bytes
            appAttestationReport = enclaveInstanceInfo.serialize(),
            name = keySpec.name,
            masterKeyType = keySpec.masterKeyType,
            policyConstraint = keySpec.policyConstraint
        )

        con.outputStream.use {
            jsonMapper.writeValue(it, kdsPrivateKeyRequest)
        }

        if (con.responseCode != HttpURLConnection.HTTP_OK) {
            val errorText = (con.errorStream ?: con.inputStream).use { it.reader().readText() }
            val kdsErrorResponse =  try {
                jsonMapper.readValue(errorText, KDSErrorResponse::class.java)
            } catch (e: Exception) {
                // It is likely that the error response is not a KDSErrorResponse if an exception is raised
                // The best thing to do in those cases is to return the response code
                throw IOException("HTTP response code: ${con.responseCode}, HTTP response message: $errorText")
            }
            throw IOException(kdsErrorResponse.reason)
        }

        val kdsPrivateKeyResponse = con.inputStream.use {
            jsonMapper.readValue(it, KDSPrivateKeyResponse::class.java)
        }
        return kdsPrivateKeyResponse
    }

    /**
     * Perform a fresh attestation with the attestation service. On successful completion the [enclaveInstanceInfo]
     * property may be updated to a newer one. If so make sure to provide this to end clients.
     *
     * Note that an attestation is already performed on startup. It's recommended to call this method if a long time
     * has passed and clients may want a more fresh version.
     */
    @Synchronized
    fun updateAttestation() {
        val attestation = getAttestation()
        updateEnclaveInstanceInfo(attestation)
    }

    private fun getAttestation(): Attestation {
        val quotingEnclaveTargetInfo = quotingService.initializeQuote()
        log.debug { "Quoting enclave's target info $quotingEnclaveTargetInfo" }
        val signedQuote = enclaveHandle.enclaveInterface.getEnclaveInstanceInfoQuote(quotingEnclaveTargetInfo)
        log.debug { "Got quote $signedQuote" }
        return attestationService.attestQuote(signedQuote)
    }

    private fun updateEnclaveInstanceInfo(attestation: Attestation) {
        _enclaveInstanceInfo = EnclaveInstanceInfoImpl(
            setEnclaveInfoCallHandler.enclaveInfo.signatureKey,
            setEnclaveInfoCallHandler.enclaveInfo.encryptionKey,
            attestation
        )
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
     * For this method to work the enclave class must override and implement [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost] The return
     * value from that method (which can be null) is returned here. It will not be received via the provided callback.
     *
     * With the provided callback the enclave also has the option of using
     * [com.r3.conclave.enclave.Enclave.callUntrustedHost] and sending/receiving byte arrays in the opposite
     * direction. By chaining callbacks together, a kind of virtual stack can be constructed
     * allowing complex back-and-forth conversations between enclave and untrusted host.
     *
     * Any uncaught exceptions thrown by [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost] will propagate across the enclave-host boundary and
     * will be rethrown here.
     *
     * @param bytes Bytes to send to the enclave.
     * @param callback Bytes received from the enclave via [com.r3.conclave.enclave.Enclave.callUntrustedHost].
     *
     * @return The return value of the enclave's [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost].
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation of
     * [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost].
     * @throws IllegalStateException If the host has not been started.
     * @throws EnclaveException If an exception is raised from within the enclave.
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
     * For this method to work the enclave class must override and implement [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost] The return
     * value from that method (which can be null) is returned here. It will not be received via the provided callback.
     *
     * The enclave does not have the option of using [com.r3.conclave.enclave.Enclave.callUntrustedHost] for
     * sending bytes back to the host. Use the overload which takes in a callback [Function] instead.
     *
     * Any uncaught exceptions thrown by [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost] will propagate
     * across the enclave-host boundary and will be rethrown here.
     *
     * @param bytes Bytes to send to the enclave.
     *
     * @return The return value of the enclave's [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost].
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation of [com.r3.conclave.enclave.Enclave.receiveFromUntrustedHost].
     * @throws IllegalStateException If the host has not been started.
     * @throws EnclaveException If an exception is raised from within the enclave.
     */
    fun callEnclave(bytes: ByteArray): ByteArray? = callEnclaveInternal(bytes, null)

    private fun callEnclaveInternal(bytes: ByteArray, callback: EnclaveCallback?): ByteArray? {
        return checkStateFirst { enclaveMessageHandler.callEnclave(bytes, callback) }
    }

    /**
     * Delivers the given encrypted mail bytes to the enclave. The enclave is required to override and implement
     * [com.r3.conclave.enclave.Enclave.receiveMail]
     * to receive it. If the enclave throws an exception it will be rethrown.
     * It's up to the caller to decide what to do with mails that don't seem to be
     * handled properly: discarding it and logging an error is a simple option, or
     * alternatively queuing it to disk in anticipation of a bug fix or upgrade
     * is also workable.
     *
     * It's possible the callback provided to [start] will receive a [MailCommand.PostMail]
     * on the same thread, requesting mail to be sent back in response. However, it's
     * also possible the enclave will hold the mail without requesting any action.
     *
     * If the enclave is not unable to decrypt the mail bytes then a [MailDecryptionException] is thrown. This can
     * happen if the mail is not encrypted to the enclave's key, which will most likely occur if the enclave was
     * restarted and the client had used the enclave's old encryption key. In such a scenerio the client must be
     * informed so that it re-send the mail using the enclave's new encryption key.
     *
     * @param mail The encrypted mail received from a remote client.
     * @param routingHint An arbitrary bit of data identifying the sender on the host side. The enclave can pass this
     * back through to [MailCommand.PostMail] to ask the host to deliver the reply to the right location.
     * @param callback If the enclave calls [com.r3.conclave.enclave.Enclave.callUntrustedHost] then the
     * bytes will be passed to this object for consumption and generation of the
     * response.
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation for
     * [com.r3.conclave.enclave.Enclave.receiveMail].
     * @throws MailDecryptionException If the enclave was unable to decrypt the mail due to either key mismatch or
     * corrupted mail bytes.
     * @throws IOException If the mail is encrypted with a KDS private key and the host was unable to communicate
     * with the KDS to get it.
     * @throws IllegalStateException If the host has not been started.
     * @throws EnclaveException If an exception is raised from within the enclave.
     */
    @Throws(MailDecryptionException::class, IOException::class)
    fun deliverMail(mail: ByteArray, routingHint: String?, callback: Function<ByteArray, ByteArray?>) {
        deliverMailInternal(mail, routingHint, callback)
    }

    /**
     * Delivers the given encrypted mail bytes to the enclave. The enclave is required to override and implement
     * [com.r3.conclave.enclave.Enclave.receiveMail]
     * to receive it. If the enclave throws an exception it will be rethrown.
     * It's up to the caller to decide what to do with mails that don't seem to be
     * handled properly: discarding it and logging an error is a simple option, or
     * alternatively queuing it to disk in anticipation of a bug fix or upgrade
     * is also workable.
     *
     * It's possible the callback provided to [start] will receive a [MailCommand.PostMail]
     * on the same thread, requesting mail to be sent back in response. However, it's
     * also possible the enclave will hold the mail without requesting any action.
     *
     * If the enclave is not unable to decrypt the mail bytes then a [MailDecryptionException] is thrown. This can
     * happen if the mail is not encrypted to the enclave's key, which will most likely occur if the enclave was
     * restarted and the client had used the enclave's old encryption key. In such a scenerio the client must be
     * informed so that it re-send the mail using the enclave's new encryption key.
     *
     * Note: The enclave does not have the option of using [com.r3.conclave.enclave.Enclave.callUntrustedHost] for
     * sending bytes back to the host. Use the overload which takes in a callback [Function] instead.
     *
     * @param mail the encrypted mail received from a remote client.
     * @param routingHint An arbitrary bit of data identifying the sender on the host side. The enclave can pass this
     * back through to [MailCommand.PostMail] to ask the host to deliver the reply to the right location.
     *
     * @throws UnsupportedOperationException If the enclave has not provided an implementation for [com.r3.conclave.enclave.Enclave.receiveMail].
     * @throws MailDecryptionException If the enclave was unable to decrypt the mail due to either key mismatch or
     * corrupted mail bytes.
     * @throws IOException If the mail is encrypted with a KDS private key and the host was unable to communicate
     * with the KDS to get it.
     * @throws IllegalStateException If the host has not been started.
     * @throws EnclaveException If an exception is raised from within the enclave.
     */
    @Throws(MailDecryptionException::class, IOException::class)
    fun deliverMail(mail: ByteArray, routingHint: String?) = deliverMailInternal(mail, routingHint, null)

    private fun deliverMailInternal(mail: ByteArray, routingHint: String?, callback: EnclaveCallback?) {
        return checkStateFirst { enclaveMessageHandler.deliverMail(mail, callback, routingHint) }
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
            // Ask the enclave to close so all its resources are released before the enclave is destroyed
            enclaveHandle.enclaveInterface.stopEnclave()

            // Destroy the enclave
            enclaveHandle.destroy()

            fileSystemHandler?.close()
        } finally {
            hostStateManager.state = Closed
        }
    }

    private class EnclaveInfo(val signatureKey: PublicKey, val encryptionKey: Curve25519PublicKey)

    /**
     * Handler for servicing requests from the enclave for signed quotes.
     */
    private inner class GetSignedQuoteHandler : CallHandler {
        override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer {
            val report = Cursor.slice(SgxReport, parameterBuffer)
            val signedQuote = quotingService.retrieveQuote(report)
            return signedQuote.buffer
        }
    }

    /**
     * Handler for servicing requests from the enclave for quoting info.
     */
    private inner class GetQuotingEnclaveInfoHandler : CallHandler {
        override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer {
            return quotingService.initializeQuote().buffer
        }
    }

    /**
     * Handler for servicing attestation requests from the enclave.
     */
    private inner class GetAttestationHandler : CallHandler {
        override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer {
            val attestationBytes = writeData { _enclaveInstanceInfo!!.attestation.writeTo(this) }
            return ByteBuffer.wrap(attestationBytes)
        }
    }

    /**
     * Handler for receiving enclave info from the enclave on initialisation.
     * TODO: It would be better to return enclave info from the initialise enclave call
     *       but that doesn't work in mock mode at the moment.
     */
    private inner class SetEnclaveInfoCallHandler : CallHandler {
        private var _enclaveInfo: EnclaveInfo? = null
        val enclaveInfo: EnclaveInfo get() = checkNotNull(_enclaveInfo) { "Not received enclave info" }

        override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
            val signatureKey = signatureScheme.decodePublicKey(parameterBuffer.getBytes(44))
            val encryptionKey = Curve25519PublicKey(parameterBuffer.getBytes(32))
            _enclaveInfo = EnclaveInfo(signatureKey, encryptionKey)
            return null
        }
    }

    private class Transaction {
        val stateManager = StateManager<CallState>(Ready)
        val mailCommands = LinkedList<MailCommand>()

        fun fireMailCommands(commandsCallback: Consumer<List<MailCommand>>) {
            check(mailCommands.isNotEmpty())
            val commandsCopy = ArrayList(mailCommands)
            mailCommands.clear()
            commandsCallback.accept(commandsCopy)
        }
    }

    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private inner class EnclaveMessageHandler : CallHandler {
        private val callTypeValues = InternalCallType.values()
        @PotentialPackagePrivate("Access for EnclaveHostMockTest")
        private val threadIDToTransaction = ConcurrentHashMap<Long, Transaction>()
        // Try to reduce the number of HTTP requests to the KDS, which also has the benefit for reducing the number
        // large ECALLs containing the KDS mail response and the KDE EII bytes (since the enclave also caches the
        // private key).
        private val seenKdsKeySpecs = ConcurrentHashMap.newKeySet<KDSKeySpec>()

        override fun handleCall(parameterBuffer: ByteBuffer): ByteBuffer? {
            val type = callTypeValues[parameterBuffer.get().toInt()]
            val threadID = parameterBuffer.getLong()
            val transaction = threadIDToTransaction.getValue(threadID)
            val callStateManager = transaction.stateManager
            val intoEnclaveState = callStateManager.checkStateIs<IntoEnclave>()
            when (type) {
                MAIL -> onMail(transaction, parameterBuffer)
                UNTRUSTED_HOST -> onUntrustedHost(intoEnclaveState, threadID, parameterBuffer)
                CALL_RETURN -> onCallReturn(callStateManager, parameterBuffer)
                SEALED_STATE -> onSealedState(transaction, parameterBuffer)
            }
            return null
        }

        private fun onMail(transaction: Transaction, input: ByteBuffer) {
            // routingHint can be null/missing.
            val routingHint = input.getNullable { getIntLengthPrefixString() }
            // rest of the body to deliver (should be encrypted).
            val encryptedBytes = input.getRemainingBytes()
            val cmd = MailCommand.PostMail(encryptedBytes, routingHint)
            transaction.mailCommands.add(cmd)
        }

        private fun onUntrustedHost(intoEnclaveState: IntoEnclave, threadID: Long, input: ByteBuffer) {
            val bytes = input.getRemainingBytes()
            requireNotNull(intoEnclaveState.callback) {
                "Enclave responded via callUntrustedHost but a callback was not provided to callEnclave."
            }
            val response = intoEnclaveState.callback.apply(bytes)
            if (response != null) {
                sendToEnclave(CALL_RETURN, threadID, response.size) { buffer ->
                    buffer.put(response)
                }
            }
        }

        private fun onCallReturn(callStateManager: StateManager<CallState>, input: ByteBuffer) {
            callStateManager.state = Response(input.getRemainingBytes())
        }

        private fun onSealedState(transaction: Transaction, input: ByteBuffer) {
            val sealedState = input.getRemainingBytes()
            val cmd = MailCommand.StoreSealedState(sealedState)
            transaction.mailCommands.add(cmd)
            // If a sealed state is received from the the enclave then it should be the last command the enclave sends
            // to the host. It triggers an execution of the commands callback. We do this here whilst the thread still
            // has the internal enclave lock, thus making sure the sealed states are emitted in the order the enclave
            // wishes.
            transaction.fireMailCommands(commandsCallback)
        }

        fun callEnclave(bytes: ByteArray, callback: EnclaveCallback?): ByteArray? {
            // To support concurrent calls into the enclave, the current thread's ID is used a call ID which is passed between
            // the host and enclave. This enables each thread to have its own state for managing the calls.
            try {
                return callIntoEnclave(callback) { threadID ->
                    sendToEnclave(UNTRUSTED_HOST, threadID, bytes.size) { buffer ->
                        buffer.put(bytes)
                    }
                }
            } catch (e: MailDecryptionException) {
                // callEnclave does not have throws declaration for MailDecryptionException (since it doesn't directly
                // deal with mail) and so must be wrapped in an unchecked exception.
                throw RuntimeException(e)
            }
        }

        fun deliverMail(mailBytes: ByteArray, callback: EnclaveCallback?, routingHint: String?) {
            // The host checks if the mail is encrypted with a KDS private key and makes the KDS HTTP request to get
            // it. This is safe to do as the key derivation field is authenticated and the KDS response is encrypted.
            // The enclave will check itself anyway that the response matches the key derivation field.
            // Doing this has two benefits:
            // 1. Avoids an unnecessary OCALL-ECALL cycle and thus simplifying the enclave code.
            // 2. Avoids any IOException that might have been thrown by the HTTP request from being swallowed in
            //    release mode enclaves.
            val mailKeyDerivation = MailKeyDerivation.deserialiseFromMailBytes(mailBytes)
            val kdsKeySpec = (mailKeyDerivation as? KdsKeySpecKeyDerivation)?.keySpec
            val privateKeyResponse = kdsKeySpec?.let { getKdsPrivateKeyResponse(kdsKeySpec) }

            callIntoEnclave(callback) { threadID ->
                val routingHintBytes = routingHint?.toByteArray()
                val routingHintSize = nullableSize(routingHintBytes) { it.intLengthPrefixSize }
                val privateKeyResponseSize = nullableSize(privateKeyResponse) { it.size }
                val size = routingHintSize + privateKeyResponseSize + mailBytes.size
                sendToEnclave(MAIL, threadID, size) { buffer ->
                    buffer.putNullable(routingHintBytes) { putIntLengthPrefixBytes(it) }
                    buffer.putNullable(privateKeyResponse) { putKdsPrivateKeyResponse(it) }
                    buffer.put(mailBytes)
                }
            }

            if (privateKeyResponse != null) {
                // It's important that we mark this key spec as having been seen only after the enclave has processed
                // the mail. In the presence of multiple threads, only here can we guarantee that it has cached the
                // private key for itself.
                seenKdsKeySpecs += kdsKeySpec
            }
        }

        private fun getKdsPrivateKeyResponse(keySpec: KDSKeySpec): KDSPrivateKeyResponse? {
            // As an optimisation avoid sending the KDS response mail and KDS EII if the enclave has already cached
            // the private key. However we can't guarantee that the enclave has cached the private key until after
            // deliverMail has returned which is why we don't update the cache here.
            return if (keySpec !in seenKdsKeySpecs) {
                val kdsConfig = checkNotNull(kdsConfiguration) {
                    "Mail is encrypted with KDS private key but host has not been provided with KDS configuration."
                }
                executeKdsPrivateKeyRequest(keySpec, kdsConfig)
            } else {
                null
            }
        }

        // Sets up the state tracking and handle re-entrancy.
        private fun callIntoEnclave(callback: EnclaveCallback?, body: (Long) -> Unit): ByteArray? {
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
            } catch (t: Throwable) {
                throw when (t) {
                    // No need to wrap an Enclave exception inside another Enclave exception
                    is EnclaveException -> t
                    // Unchecked exceptions propagate as is.
                    is RuntimeException, is Error -> t
                    // MailDecryptionException needs to propagate as is for deliverMail.
                    is MailDecryptionException -> t
                    else -> EnclaveException(null, t)
                }
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

            // If fully unwound and we still have mail commands to deliver (because a sealed state wasn't emitted) ...
            if (callStateManager.state == Ready && transaction.mailCommands.isNotEmpty()) {
                // ... the transaction ends here so pass mail commands to the host for processing.
                transaction.fireMailCommands(commandsCallback)
            }

            return response?.bytes
        }

        private fun sendToEnclave(
            type: InternalCallType,
            threadID: Long,
            payloadSize: Int,
            payload: (ByteBuffer) -> Unit
        ) {
            val buffer = ByteBuffer.allocate(1 + Long.SIZE_BYTES + payloadSize).apply {
                put(type.ordinal.toByte())
                putLong(threadID)
                payload(this)
            }
            enclaveHandle.enclaveInterface.sendMessageHandlerCommand(buffer)
        }
    }

    private sealed class CallState {
        object Ready : CallState()
        class IntoEnclave(val callback: EnclaveCallback?) : CallState()
        class Response(val bytes: ByteArray) : CallState()
    }

    private sealed class HostState {
        object New : HostState()
        object Started : HostState()
        object Closed : HostState()
    }
}

// Typealias to make this code easier to read.
private typealias EnclaveCallback = Function<ByteArray, ByteArray?>
