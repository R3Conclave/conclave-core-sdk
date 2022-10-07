package com.r3.conclave.host

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.r3.conclave.common.*
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.InternalCallType.*
import com.r3.conclave.common.internal.attestation.Attestation
import com.r3.conclave.common.internal.handler.*
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.common.internal.kds.KDSErrorResponse
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.host.EnclaveHost.CallState.*
import com.r3.conclave.host.EnclaveHost.HostState.*
import com.r3.conclave.host.internal.*
import com.r3.conclave.host.internal.attestation.AttestationService
import com.r3.conclave.host.internal.attestation.AttestationServiceFactory
import com.r3.conclave.host.internal.attestation.EnclaveQuoteService
import com.r3.conclave.host.internal.attestation.EnclaveQuoteServiceFactory
import com.r3.conclave.host.internal.fatfs.FileSystemHandler
import com.r3.conclave.host.internal.kds.KDSPrivateKeyRequest
import com.r3.conclave.host.internal.kds.KDSPrivateKeyResponse
import com.r3.conclave.host.kds.KDSConfiguration
import com.r3.conclave.mail.Curve25519PublicKey
import com.r3.conclave.mail.MailDecryptionException
import com.r3.conclave.utilities.internal.*
import io.github.classgraph.ClassGraph
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
import java.util.regex.Pattern

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
    private val enclaveHandle: EnclaveHandle<ExceptionReceivingHandler.Connection>
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
            return createEnclaveHost(EnclaveScanner.findEnclave(enclaveClassName), mockConfiguration)
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
            return createEnclaveHost(EnclaveScanner.findEnclave(), mockConfiguration)
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
        internal fun internalCreateNative(
            enclaveMode: EnclaveMode,
            enclaveFileUrl: URL,
            enclaveClassName: String,
        ): EnclaveHost {
            val enclaveHandle = NativeEnclaveHandle(enclaveMode, enclaveFileUrl, enclaveClassName, ExceptionReceivingHandler())
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
                kdsConfig,
                ExceptionReceivingHandler()
            )
            return EnclaveHost(enclaveHandle)
        }

        private fun createEnclaveHost(result: EnclaveScanner.ScanResult, mockConfiguration: MockConfiguration?): EnclaveHost {
            try {
                return when (result) {
                    is EnclaveScanner.ScanResult.Mock -> {
                        // Here we do not call checkPlatformEnclaveSupport as mock mode is supported on any platform
                        val enclaveClass = Class.forName(result.enclaveClassName)
                        internalCreateMock(enclaveClass, mockConfiguration)
                    }
                    is EnclaveScanner.ScanResult.Native -> {
                        checkPlatformEnclaveSupport(result.enclaveMode)
                        internalCreateNative(result.enclaveMode, result.soFileUrl, result.enclaveClassName)
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

        private val KDSPrivateKeyResponse.size: Int get() {
            return encryptedPrivateKey.intLengthPrefixSize + kdsAttestationReport.intLengthPrefixSize
        }

        private fun ByteBuffer.putKdsPrivateKeyResponse(response: KDSPrivateKeyResponse) {
            putIntLengthPrefixBytes(response.encryptedPrivateKey)
            putIntLengthPrefixBytes(response.kdsAttestationReport)
        }
    }

    private var kdsConfiguration: KDSConfiguration? = null
    private var fileSystemHandler: FileSystemHandler? = null

    private val hostStateManager = StateManager<HostState>(New)
    private lateinit var adminHandler: AdminHandler

    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private lateinit var enclaveMessageHandler: EnclaveMessageHandler
    private var _enclaveInstanceInfo: EnclaveInstanceInfoImpl? = null

    private lateinit var quotingEnclaveInfoHandler: QuotingEnclaveInfoHandler
    private lateinit var signedQuoteHandler: SignedQuoteHandler
    private lateinit var enclaveInstanceInfoQuoteHandler: EnclaveInstanceInfoQuoteHandler

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

        // For now, there is no enclave for Gramine instead the mock enclave is initialised.
        // This is OK because no VM is started in Mock mode and this will allow us to integrate Gramine with Conclave
        // iteratively without causing issues to the normal operation of Conclave.
        // Start Gramine only if the environment variable is set
        if(Gramine.isGramineEnabled())
        {
            if(enclaveHandle !is MockEnclaveHandle) {
                throw Exception("Gramine cannot be started in non-mock modes")
            }
            Gramine.start()
        }

        // This can throw IllegalArgumentException which we don't want wrapped in a EnclaveLoadException.
        attestationService = AttestationServiceFactory.getService(enclaveMode, attestationParameters)
        quotingService = EnclaveQuoteServiceFactory.getService(attestationParameters?.takeIf { enclaveMode.isHardware })

        try {
            this.commandsCallback = commandsCallback
            // Set up a set of channels in and out of the enclave. Each byte array sent/received comes with
            // a prefixed channel ID that lets us split them out to separate classes.
            val mux: SimpleMuxingHandler.Connection = enclaveHandle.connection.setDownstream(SimpleMuxingHandler())
            // The admin handler deserializes keys and other info from the enclave during initialisation.
            adminHandler = mux.addDownstream(AdminHandler(this))

            // Connect handlers associated with attestation
            quotingEnclaveInfoHandler = mux.addDownstream(QuotingEnclaveInfoHandler())
            signedQuoteHandler = mux.addDownstream(SignedQuoteHandler())
            enclaveInstanceInfoQuoteHandler = mux.addDownstream(EnclaveInstanceInfoQuoteHandler())

            // The enclave is initialised when it receives its first bytes. We use the request for
            // the signed quote as that trigger. Therefore we know at this point adminHandler.enclaveInfo is available
            // for us to query.
            updateAttestation()
            log.debug { enclaveInstanceInfo.toString() }

            // Once the EnclaveInstanceInfo has been updated, we can do a KDS request for the persistence key.
            if (kdsConfiguration != null) {
                this.kdsConfiguration = kdsConfiguration
                // TODO We can avoid this ECALL if we get the enclave to send its persistence key spec when it's
                //  first initialised.
                val persistenceKeySpec = adminHandler.requestPersistenceKdsKeySpec()
                //  If the enclave is configured also with KDS spec for persistence, we trigger the private key request.
                //    Note that the kdsConfiguration is also used in the context of KdsPostOffice
                if (persistenceKeySpec != null) {
                    requestPersistenceKdsPrivateKey(persistenceKeySpec, kdsConfiguration)
                }
            }

            // This handler wires up callUntrustedHost -> callEnclave and mail delivery.
            enclaveMessageHandler = mux.addDownstream(EnclaveMessageHandler())

            if (enclaveFileSystemFile != null) {
                log.info("Setting up persistent enclave file system...")
            }
            fileSystemHandler = prepareFileSystemHandler(enclaveFileSystemFile)
            adminHandler.sendOpen(sealedState)
            if (enclaveFileSystemFile != null) {
                log.info("Setup of the file system completed successfully.")
            }

            hostStateManager.state = Started
        } catch (e: Exception) {
            throw EnclaveLoadException("Unable to start enclave", e)
        }
    }

    private fun prepareFileSystemHandler(enclaveFileSystemFile: Path?): FileSystemHandler? {
        return if (enclaveMode != EnclaveMode.MOCK) {
            val fileSystemFilePaths = if (enclaveFileSystemFile != null) listOf(enclaveFileSystemFile) else emptyList()
            FileSystemHandler(fileSystemFilePaths, enclaveMode)
        } else {
            null
        }
    }

    private fun requestPersistenceKdsPrivateKey(keySpec: KDSKeySpec, kdsConfiguration: KDSConfiguration) {
        val kdsResponse = executeKdsPrivateKeyRequest(keySpec, kdsConfiguration)
        adminHandler.sendKdsPrivateKeyResponseToEnclave(
            kdsResponse,
            HostToEnclave.PERSISTENCE_KDS_PRIVATE_KEY_RESPONSE
        )
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
        val signedQuote = enclaveInstanceInfoQuoteHandler.getQuote(quotingEnclaveTargetInfo)
        log.debug { "Got quote $signedQuote" }
        return attestationService.attestQuote(signedQuote)
    }

    private fun updateEnclaveInstanceInfo(attestation: Attestation) {
        _enclaveInstanceInfo = EnclaveInstanceInfoImpl(
            adminHandler.enclaveInfo.signatureKey,
            adminHandler.enclaveInfo.encryptionKey,
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
        if(Gramine.isGramineEnabled()) {
            Gramine.stop()
        }
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
            adminHandler.sendClose()

            // Destroy the enclave
            enclaveHandle.destroy()

            fileSystemHandler?.close()
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

        private var persistenceKdsKeySpec: KDSKeySpec? = null

        private val messageTypes = EnclaveToHost.values()

        override fun connect(upstream: Sender): AdminHandler = this.also { sender = upstream }

        override fun onReceive(connection: AdminHandler, input: ByteBuffer) {
            when (messageTypes[input.get().toInt()]) {
                EnclaveToHost.ENCLAVE_INFO -> onEnclaveInfo(input)
                EnclaveToHost.ATTESTATION -> onAttestation()
                EnclaveToHost.PERSISTENCE_KDS_KEY_SPEC_RESPONSE -> onPersistenceKdsKeySpecResponse(input)
            }
        }

        private fun onEnclaveInfo(input: ByteBuffer) {
            check(_enclaveInfo == null) { "Already received enclave info" }
            val signatureKey = signatureScheme.decodePublicKey(input.getBytes(44))
            val encryptionKey = Curve25519PublicKey(input.getBytes(32))
            _enclaveInfo = EnclaveInfo(signatureKey, encryptionKey)
        }

        private fun onAttestation() {
            val attestationBytes = writeData { host._enclaveInstanceInfo!!.attestation.writeTo(this) }
            sendToEnclave(HostToEnclave.ATTESTATION, attestationBytes.size) { buffer ->
                buffer.put(attestationBytes)
            }
        }

        private fun onPersistenceKdsKeySpecResponse(input: ByteBuffer) {
            check(persistenceKdsKeySpec == null)
            persistenceKdsKeySpec = getKdsKeySpec(input)
        }

        fun sendOpen(sealedState: ByteArray?) {
            val payloadSize = nullableSize(sealedState) { it.size }
            sendToEnclave(HostToEnclave.OPEN, payloadSize) { buffer ->
                buffer.putNullable(sealedState) { put(it) }
            }
        }

        fun sendClose() {
            sendToEnclave(HostToEnclave.CLOSE, 0) { }
        }

        fun requestPersistenceKdsKeySpec(): KDSKeySpec? {
            sendToEnclave(HostToEnclave.PERSISTENCE_KDS_KEY_SPEC_REQUEST, 0) { }
            return persistenceKdsKeySpec
        }

        fun sendKdsPrivateKeyResponseToEnclave(response: KDSPrivateKeyResponse, kdsResponseType: HostToEnclave) {
            sendToEnclave(kdsResponseType, response.size) { buffer ->
                buffer.putKdsPrivateKeyResponse(response)
            }
        }

        private fun sendToEnclave(type: HostToEnclave, payloadSize: Int, payload: (ByteBuffer) -> Unit) {
            sender.send(1 + payloadSize) { buffer ->
                buffer.put(type.ordinal.toByte())
                payload(buffer)
            }
        }

        private fun getKdsKeySpec(input: ByteBuffer): KDSKeySpec {
            val name = input.getIntLengthPrefixString()
            val masterKeyType = MasterKeyType.fromID(input.get().toInt())
            val policyConstraint = input.getRemainingString()
            return KDSKeySpec(name, masterKeyType, policyConstraint)
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

    /**
     * Handler for servicing requests from the enclave for signed quotes.
     */
    private inner class SignedQuoteHandler : Handler<SignedQuoteHandler> {
        private lateinit var sender: Sender

        override fun connect(upstream: Sender): SignedQuoteHandler {
            sender = upstream
            return this
        }

        override fun onReceive(connection: SignedQuoteHandler, input: ByteBuffer) {
            val report = Cursor.slice(SgxReport, input)
            val signedQuote = quotingService.retrieveQuote(report)
            sender.send(signedQuote.size) { buffer -> buffer.put(signedQuote.buffer) }
        }
    }

    /**
     * Handler for servicing requests from the enclave for quoting info.
     */
    private inner class QuotingEnclaveInfoHandler : Handler<QuotingEnclaveInfoHandler> {
        private lateinit var sender: Sender

        override fun connect(upstream: Sender): QuotingEnclaveInfoHandler {
            sender = upstream
            return this
        }

        override fun onReceive(connection: QuotingEnclaveInfoHandler, input: ByteBuffer) {
            val quotingEnclaveInfo = quotingService.initializeQuote()
            sender.send(quotingEnclaveInfo.size) { buffer -> buffer.put(quotingEnclaveInfo.buffer) }
        }
    }

    /**
     * Handler for retrieving a report from the enclave for enclave instance info.
     */
    private inner class EnclaveInstanceInfoQuoteHandler : Handler<EnclaveInstanceInfoQuoteHandler> {
        private lateinit var sender: Sender

        private val quote = ThreadLocal<ByteCursor<SgxSignedQuote>>()

        override fun connect(upstream: Sender): EnclaveInstanceInfoQuoteHandler {
            sender = upstream
            return this
        }

        override fun onReceive(connection: EnclaveInstanceInfoQuoteHandler, input: ByteBuffer) {
            check(quote.get() == null)
            quote.set(Cursor.copy(SgxSignedQuote, input))
        }

        fun getQuote(target: ByteCursor<SgxTargetInfo>): ByteCursor<SgxSignedQuote> {
            try {
                sender.send(target.size) { buffer ->
                    buffer.put(target.buffer)
                }
                return checkNotNull(quote.get())
            } finally {
                quote.remove()
            }
        }
    }

    @PotentialPackagePrivate("Access for EnclaveHostMockTest")
    private inner class EnclaveMessageHandler : Handler<EnclaveMessageHandler> {
        private lateinit var sender: Sender

        private val callTypeValues = InternalCallType.values()
        @PotentialPackagePrivate("Access for EnclaveHostMockTest")
        private val threadIDToTransaction = ConcurrentHashMap<Long, Transaction>()
        // Try to reduce the number of HTTP requests to the KDS, which also has the benefit for reducing the number
        // large ECALLs containing the KDS mail response and the KDE EII bytes (since the enclave also caches the
        // private key).
        private val seenKdsKeySpecs = ConcurrentHashMap.newKeySet<KDSKeySpec>()

        override fun connect(upstream: Sender): EnclaveMessageHandler = this.also { sender = upstream }

        override fun onReceive(connection: EnclaveMessageHandler, input: ByteBuffer) {
            val type = callTypeValues[input.get().toInt()]
            val threadID = input.getLong()
            val transaction = threadIDToTransaction.getValue(threadID)
            val callStateManager = transaction.stateManager
            val intoEnclaveState = callStateManager.checkStateIs<IntoEnclave>()
            when (type) {
                MAIL -> onMail(transaction, input)
                UNTRUSTED_HOST -> onUntrustedHost(intoEnclaveState, threadID, input)
                CALL_RETURN -> onCallReturn(callStateManager, input)
                SEALED_STATE -> onSealedState(transaction, input)
            }
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
                    // Unchecked exceptions propagate as is.
                    is RuntimeException, is Error -> t
                    // MailDecryptionException needs to propagate as is for deliverMail.
                    is MailDecryptionException -> t
                    // No need to wrap an Enclave exception inside another Enclave exception
                    is EnclaveException -> t
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
            sender.send(1 + Long.SIZE_BYTES + payloadSize) { buffer ->
                buffer.put(type.ordinal.toByte())
                buffer.putLong(threadID)
                payload(buffer)
            }
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

    /**
     * The following singleton contains the necessary functions to search for enclave classes
     **/
    private object EnclaveScanner {

        /**
         * Performs a search for an enclave by performing a classpath and module scan. There are two places where an enclave
         * can be found.
         * 1) In an SGX signed enclave file (.so)
         * 2) For mock enclaves, an existing class in the classpath
         *
         * For a .so enclave file the function looks in the classpath and search for a file name that matches the pattern
         * ^.*(simulation|debug|release)\.signed\.so$.
         *
         * For mock enclaves, the function searches for classes that extend com.r3.conclave.enclave.Enclave.
         *
         * If more than one enclave is found (i.e. multiple modes, or mock + signed enclave) then an exception is thrown.
         *
         * For .so enclaves, the mode is derived from the filename but is not taken at face value. The construction of the
         * EnclaveInstanceInfoImpl in `start` makes sure the mode is correct it terms of the remote attestation.
         *
         * @return Pair with the class name and URL (if not a mock enclave) of the enclave found.
         */
        fun findEnclave(): ScanResult {
            val classGraph = ClassGraph()
            val results = ArrayList<ScanResult>()
            findNativeEnclaves(classGraph, results)
            findMockEnclaves(classGraph, results)
            return getSingleResult(results, null)
        }

        /**
         * Searches for an enclave. There are two places where an enclave can be found.
         * 1) In an SGX signed enclave file (.so)
         * 2) For mock enclaves, an existing class named 'className' in the classpath
         *
         * For a .so enclave file the function looks in the classpath at /package/namespace/classname-mode.signed.so.
         * For example, it will look for the enclave file of "com.foo.bar.Enclave" at /com/foo/bar/Enclave-$mode.signed.so.
         *
         * For mock enclaves, the function just determines whether the class specified as 'className' exists.
         *
         * If more than one enclave is found (i.e. multiple modes, or mock + signed enclave) then an exception is thrown.
         *
         * For .so enclaves, the mode is derived from the filename but is not taken at face value. The construction of the
         * EnclaveInstanceInfoImpl in `start` makes sure the mode is correct it terms of the remote attestation.
         */
        fun findEnclave(enclaveClassName: String): ScanResult {
            val results = ArrayList<ScanResult>()
            EnclaveMode.values().mapNotNullTo(results) { findNativeEnclave(enclaveClassName, it) }
            findMockEnclave(enclaveClassName)?.let { results += it }
            return getSingleResult(results, enclaveClassName)
        }

        private fun findNativeEnclave(enclaveClassName: String, enclaveMode: EnclaveMode): ScanResult.Native? {
            val resourceName = "/${enclaveClassName.replace('.', '/')}-${enclaveMode.name.lowercase()}.signed.so"
            val url = EnclaveHost::class.java.getResource(resourceName)
            return url?.let { ScanResult.Native(enclaveClassName, enclaveMode, url) }
        }

        private fun findMockEnclave(enclaveClassName: String): ScanResult.Mock? {
            return try {
                Class.forName(enclaveClassName)
                ScanResult.Mock(enclaveClassName)
            } catch (e: ClassNotFoundException) {
                null
            }
        }

        /**
         * Performs a search for Intel SGX signed enclave files (.so) using ClassGraph.
         */
        private fun findNativeEnclaves(classGraph: ClassGraph, results: MutableList<ScanResult>) {
            val nativeSoFilePattern = Pattern.compile("""^(.+)-(simulation|debug|release)\.signed\.so$""")

            classGraph.scan().use {
                for (resource in it.allResources) {
                    val pathMatcher = nativeSoFilePattern.matcher(resource.path)
                    if (pathMatcher.matches()) {
                        val enclaveClassName = pathMatcher.group(1).replace('/', '.')
                        val enclaveMode = EnclaveMode.valueOf(pathMatcher.group(2).uppercase())
                        results += ScanResult.Native(enclaveClassName, enclaveMode, resource.url)
                    }
                }
            }
        }

        /**
         * Performs a search for mock enclave files using ClassGraph. The search is performed by looking for classes that
         * extend com.r3.conclave.enclave.Enclave.
         */
        private fun findMockEnclaves(classGraph: ClassGraph, results: MutableList<ScanResult>) {
            classGraph.enableClassInfo().scan().use {
                for (classInfo in it.getSubclasses("com.r3.conclave.enclave.Enclave")) {
                    if (!classInfo.isAbstract) {
                        results += ScanResult.Mock(classInfo.name)
                    }
                }
            }
        }

        private fun getSingleResult(results: List<ScanResult>, className: String?): ScanResult {
            when (results.size) {
                1 -> return results[0]
                0 -> {
                    val beginning = if (className != null) "Enclave $className does not exist" else "No enclaves found"
                    throw IllegalArgumentException(
                        """$beginning on the classpath. Please make sure the gradle dependency to the enclave project is correctly specified:
                            |    runtimeOnly project(path: ":enclave project", configuration: mode)
                            |
                            |    where:
                            |      mode is either "release", "debug", "simulation" or "mock"
                            """.trimMargin()
                    )
                }
                else -> throw IllegalStateException("Multiple enclaves were found: $results")
            }
        }

        sealed class ScanResult {
            class Mock(val enclaveClassName: String) : ScanResult() {
                override fun toString(): String = "mock $enclaveClassName"
            }

            class Native(
                val enclaveClassName: String,
                val enclaveMode: EnclaveMode,
                val soFileUrl: URL
            ) : ScanResult() {
                init {
                    require(enclaveMode != EnclaveMode.MOCK)
                }
                override fun toString(): String = "${enclaveMode.name.lowercase()} $enclaveClassName"
            }
        }
    }
}

// Typealias to make this code easier to read.
private typealias EnclaveCallback = Function<ByteArray, ByteArray?>
