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
import com.r3.conclave.host.internal.NativeShared
import com.r3.conclave.host.EnclaveHost.State.*
import com.r3.conclave.host.internal.*
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.PublicKey
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
 *
 * @property enclaveMode The mode the enclave is running in.
 */
class EnclaveHost @PotentialPackagePrivate private constructor(
        val enclaveMode: EnclaveMode,
        private val enclaveHandle: EnclaveHandle<ErrorHandler.Connection>,
        private val isMock: Boolean,
        private val fileToDelete: Path?
) : AutoCloseable {
    companion object {
        private val log = loggerFor<EnclaveHost>()
        private val signatureScheme = SignatureSchemeEdDSA()

        // This wouldn't be needed if the c'tor was package-private.
        internal fun create(
                enclaveMode: EnclaveMode,
                handle: EnclaveHandle<ErrorHandler.Connection>,
                fileToDelete: Path?,
                isMock: Boolean = false
        ): EnclaveHost {
            return EnclaveHost(enclaveMode, handle, isMock, fileToDelete)
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
            val (stream, mode) = findEnclaveFile(enclaveClassName)
            val enclaveFile = try {
                Files.createTempFile(enclaveClassName, "signed.so")
            } catch (e: Exception) {
                throw EnclaveLoadException("Unable to load enclave", e)
            }
            try {
                stream.use { Files.copy(it, enclaveFile, REPLACE_EXISTING) }
                return createHost(enclaveFile, enclaveClassName, mode, tempFile = true)
            } catch (e: Exception) {
                enclaveFile.deleteQuietly()
                if (e is EnclaveLoadException) throw e
                throw EnclaveLoadException("Unable to load enclave", e)
            }
        }

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

    private val stateManager = StateManager<State>(New)
    private lateinit var enclaveSender: Sender
    private var _enclaveInstanceInfo: EnclaveInstanceInfo? = null

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
        checkNotClosed()
        require(spid == null || spid.size == 16) { "Invalid SPID length" }
        if (stateManager.state != New) return
        try {
            val mux = enclaveHandle.connection.setDownstream(SimpleMuxingHandler())
            enclaveSender = mux.addDownstream(HostHandler(this))
            // TODO We could probably simplify things if we didn't multiplex the attestation, and instead rolled it into
            //      the main host handler.
            val signedQuote = mux.addDownstream(EpidAttestationHostHandler(
                    EpidAttestationHostConfiguration(
                            SgxQuoteType.LINKABLE,
                            spid?.let { Cursor(SgxSpid, it.buffer()) } ?: Cursor.allocate(SgxSpid)
                    ),
                    isMock
            )).getSignedQuote()
            val started = stateManager.checkStateIs<Started>()
            val enclaveInstanceInfo = getAttestationService(attestationKey).doAttest(started.signatureKey, signedQuote, enclaveMode)
            log.debug { "Attestation report: ${enclaveInstanceInfo.attestationReport}" }
            log.info(enclaveInstanceInfo.toString())
            _enclaveInstanceInfo = enclaveInstanceInfo
        } catch (e: Exception) {
            throw EnclaveLoadException("Unable to start enclave", e)
        }
    }

    private fun getAttestationService(attestationKey: String?): AttestationService {
        return when (enclaveMode) {
            EnclaveMode.RELEASE -> IntelAttestationService(true, requiredAttestationKey(attestationKey))
            EnclaveMode.DEBUG -> IntelAttestationService(false, requiredAttestationKey(attestationKey))
            EnclaveMode.SIMULATION -> MockAttestationService()
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
     * [Enclave.callUntrustedHost] and sending/receiving byte arrays in the opposite
     * direction. By chaining callbacks together, a kind of virtual stack can be constructed
     * allowing complex back-and-forth conversations between enclave and untrusted host.
     *
     * @param bytes Bytes to send to the enclave.
     * @param callback Bytes received from the enclave via [Enclave.callUntrustedHost].
     *
     * @return The return value of the enclave's [EnclaveCall.invoke].
     *
     * @throws IllegalStateException If the [Enclave] does not implement [EnclaveCall]
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
     * The enclave does not have the option of using [Enclave.callUntrustedHost] for
     * sending bytes back to the host. Use the overlaod which takes in a [EnclaveCall]
     * callback instead.
     *
     * @param bytes Bytes to send to the enclave.
     *
     * @return The return value of the enclave's [EnclaveCall.invoke].
     *
     * @throws IllegalStateException If the [Enclave] does not implement [EnclaveCall]
     * or if the host has not been started.
     */
    fun callEnclave(bytes: ByteArray): ByteArray? = callEnclaveInternal(bytes, null)

    private fun callEnclaveInternal(bytes: ByteArray, callback: EnclaveCall?) : ByteArray? {
        val state = stateManager.state
        if (state is Started) {
            require(state.enclaveIsEnclaveCall) { "Enclave does not implement EnclaveCall to receive messages from the host." }
        } else {
            stateManager.checkStateIsNot<New> { "The host has not been started." }
            checkNotClosed()
        }
        // It's allowed for the host to recursively call back into the enclave with callEnclave via the callback. In this
        // scenario the "state" local variable would represent the previous call into the enclave. Once this recusive step
        // is complete we restore "state" to be the current state again so that the recursion can unwind.
        val intoEnclave = CallIntoEnclave(callback)
        stateManager.state = intoEnclave
        sendToEnclave(bytes, isEnclaveCallReturn = false)
        return if (stateManager.state == intoEnclave) {
            stateManager.state = state
            null
        } else {
            val response = stateManager.transitionStateFrom<EnclaveResponse>(to = state)
            response.bytes
        }
    }

    private fun onReceive(input: ByteBuffer) {
        when (val state = stateManager.state) {
            New -> {
                // On start the host requests for the enclave's quote. Once it's sent that it also sends the init message.
                val isEnclaveCall = input.getBoolean()
                val signatureKey = signatureScheme.decodePublicKey(input.getRemainingBytes())
                stateManager.state = Started(isEnclaveCall, signatureKey)
            }
            is CallIntoEnclave -> {
                // This is unpacking Enclave.sendToHost. We only expect the enclave to respond back to us with this
                // after we've first called into it using callEnclave.
                //
                // isEnclaveCallReturn tells us whether the enclave is sending back the result of its EnclaveCall.invoke
                // or if it's a call back to the host from within EnclaveCall.invoke. In the former case the result is
                // returned from callEnclave, in the later case it's instead sent to the calllback provided to callEnclave.
                val isEnclaveCallReturn = input.getBoolean()
                val bytes = input.getRemainingBytes()
                if (isEnclaveCallReturn) {
                    stateManager.state = EnclaveResponse(bytes)
                } else {
                    requireNotNull(state.callback) {
                        "Enclave responded via callUntrustedHost but a callback was not provided to callEnclave."
                    }
                    val response = state.callback.invoke(bytes)
                    if (response != null) {
                        sendToEnclave(response, isEnclaveCallReturn = true)
                    }
                }
            }
            else -> throw IllegalStateException(state.toString())
        }
    }

    private fun checkNotClosed() {
        stateManager.checkStateIsNot<Closed> { "The host has been closed." }
    }

    private fun sendToEnclave(bytes: ByteArray, isEnclaveCallReturn: Boolean) {
        enclaveSender.send(1 + bytes.size, Consumer { buffer ->
            buffer.putBoolean(isEnclaveCallReturn)
            buffer.put(bytes)
        })
    }

    // TODO deliverMail

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
        if (stateManager.state == Closed || stateManager.state == New) return

        fileToDelete?.deleteQuietly()
        enclaveHandle.destroy()
        stateManager.state = Closed
    }

    private class HostHandler(private val host: EnclaveHost) : Handler<Sender> {
        override fun connect(upstream: Sender): Sender = upstream
        override fun onReceive(connection: Sender, input: ByteBuffer) = host.onReceive(input)
    }

    private sealed class State {
        object New : State()
        class Started(val enclaveIsEnclaveCall: Boolean, val signatureKey: PublicKey) : State()
        class CallIntoEnclave(val callback: EnclaveCall?) : State()
        class EnclaveResponse(val bytes: ByteArray) : State()
        object Closed : State()
    }
}

/**
 * Passes the given byte array to the enclave. The format of the byte
 * arrays are up to you but will typically use some sort of serialization
 * mechanism, alternatively, [DataOutputStream] is a convenient way to lay out
 * pieces of data in a fixed order.
 *
 * For this method to work the enclave class must implement [EnclaveCall]. The return
 * value of [EnclaveCall.invoke] (which can be null) is returned here.
 *
 * The enclave does not have the option of using [Enclave.callUntrustedHost] for
 * sending bytes back to the host. Use the overlaod which takes in a [EnclaveCall]
 * callback instead.
 *
 * @param bytes Bytes to send to the enclave.
 *
 * @return The return value of the enclave's [EnclaveCall.invoke].
 *
 * @throws IllegalStateException If the [Enclave] does not implement [EnclaveCall]
 * or if the host has not been started.
 */
fun EnclaveHost.callEnclave(bytes: ByteArray, callback: (ByteArray) -> ByteArray?): ByteArray? {
    return callEnclave(bytes, EnclaveCall { callback(it) })
}
