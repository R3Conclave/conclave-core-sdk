package com.r3.conclave.host

import com.r3.conclave.common.enclave.EnclaveCall
import com.r3.conclave.common.internal.*
import com.r3.conclave.host.EnclaveHost.State.*
import com.r3.conclave.host.internal.AttestationService
import com.r3.conclave.host.internal.IntelAttestationService
import com.r3.conclave.host.internal.MockAttestationService
import com.r3.sgx.core.common.*
import com.r3.sgx.core.host.*
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.*
import java.util.function.Consumer
import kotlin.Exception

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
class EnclaveHost @PotentialPackagePrivate private constructor(
        private val handle: EnclaveHandle<ErrorHandler.Connection>,
        private val attestationService: AttestationService,
        private val fileToDelete: Path?
) : AutoCloseable {
    companion object {
        // TODO Require the user to provide these
        private val r3EpidSpid: ByteArray
        private val r3SubscriptionKey: String

        init {
            val raProperties = this::class.java.getResourceAsStream("/ra.properties").use {
                Properties().apply { load(it) }
            }
            r3EpidSpid = parseHex(raProperties.getProperty("epidSpid"))
            r3SubscriptionKey = raProperties.getProperty("iasSubscriptionKey")
        }

        private val attestationConfig = EpidAttestationHostConfiguration(
                // TODO Does the quote type need to be configurable?
                quoteType = SgxQuoteType.LINKABLE.value,
                spid = Cursor.wrap(SgxSpid, r3EpidSpid)
        )

        // This wouldn't be needed if the c'tor was package-private.
        internal fun create(
                handle: EnclaveHandle<ErrorHandler.Connection>,
                attestationService: AttestationService,
                fileToDelete: Path?
        ): EnclaveHost {
            return EnclaveHost(handle, attestationService, fileToDelete)
        }

        @PotentialPackagePrivate
        internal fun create(enclaveFile: Path, mode: EnclaveLoadMode, tempFile: Boolean): EnclaveHost {
            val handle = NativeHostApi(mode).createEnclave(ThrowingErrorHandler(), enclaveFile.toFile())
            val attestationService = when (mode) {
                EnclaveLoadMode.RELEASE -> IntelAttestationService("https://api.trustedservices.intel.com/sgx", r3SubscriptionKey)
                EnclaveLoadMode.DEBUG -> IntelAttestationService("https://api.trustedservices.intel.com/sgx/dev", r3SubscriptionKey)
                EnclaveLoadMode.SIMULATION -> MockAttestationService()
            }
            return create(handle, attestationService, if (tempFile) enclaveFile else null)
        }

        /**
         * Returns a [EnclaveHost] object initialised from the signed enclave
         * library file (ending in `.signed.so` on Linux) and in which the provided
         * local directory is used for sealed storage.
         *
         * @throws InvalidEnclaveException if something goes wrong during the load.
         */
        // TODO Remove the need for EnclaveMode
        @JvmStatic
        @Throws(InvalidEnclaveException::class)
        fun loadFromDisk(enclaveFile: Path, mode: EnclaveMode): EnclaveHost {
            try {
                return create(enclaveFile, mode.toInternalMode(), tempFile = false)
            } catch (e: Exception) {
                throw InvalidEnclaveException("Unable to load enclave", e)
            }
        }

        /**
         * Looks up the signed enclave library file from the Java class/module
         * path and loads it. The signed file is expected to be placed in the
         * default location used by the Gradle plugin, formed by taking the
         * file name the class name would be in and transforming it like so:
         *
         * com.example.FoobarEnclave
         *
         * becomes
         *
         * com/example/FoobarEnclave.signed.so
         *
         * @throws IllegalArgumentException if the enclave file cannot be found.
         * @throws InvalidEnclaveException if something goes wrong during the load.
         */
        @JvmStatic
        @Throws(InvalidEnclaveException::class)
        fun loadFromResources(enclaveClassName: String, mode: EnclaveMode): EnclaveHost {
            val resourceName = "/${enclaveClassName.replace('.', '/')}.signed.so"
            val stream = EnclaveHost::class.java.getResourceAsStream(resourceName)
            requireNotNull(stream) {
                """Enclave file for $enclaveClassName does not exist on the classpath. Please make sure the gradle dependency to the enclave project is correctly specified:
                    |    runtimeOnly project(path: ":enclave project", configuration: mode)
                    |    
                    |    where:
                    |      mode is either "release", "debug" or "simulation"
                """.trimMargin()
            }
            val enclaveFile = try {
                Files.createTempFile(enclaveClassName, "signed.so")
            } catch (e: Exception) {
                throw InvalidEnclaveException("Unable to load enclave", e)
            }
            try {
                stream.use { Files.copy(it, enclaveFile, REPLACE_EXISTING) }
                return create(enclaveFile, mode.toInternalMode(), tempFile = true)
            } catch (e: Exception) {
                enclaveFile.deleteQuietly()
                throw InvalidEnclaveException("Unable to load enclave", e)
            }
        }

        private fun EnclaveMode.toInternalMode(): EnclaveLoadMode {
            return when (this) {
                EnclaveMode.RELEASE -> EnclaveLoadMode.RELEASE
                EnclaveMode.DEBUG -> EnclaveLoadMode.DEBUG
                EnclaveMode.SIMULATION -> EnclaveLoadMode.SIMULATION
            }
        }

        private fun Path.deleteQuietly() {
            try {
                Files.deleteIfExists(this)
            } catch (e: IOException) {
                // Ignore
            }
        }

        // TODO load enclave file from memory
    }

    private val stateManager = StateManager<State>(New)
    private lateinit var sender: Sender

    /**
     * Causes the enclave to be loaded and the [Enclave] object constructed inside.
     * This method must be called before sending is possible. Remember to call
     * [close] to free the associated enclave resources when you're done with it.
     */
    @Throws(InvalidEnclaveException::class)
    // TODO MailHandler parameter
    fun start() {
        checkNotClosed()
        if (stateManager.state != New) return
        try {
            val mux = handle.connection.setDownstream(SimpleMuxingHandler())
            sender = mux.addDownstream(HostHandler(this))
            // TODO RA
            val attestation = mux.addDownstream(EpidAttestationHostHandler(attestationConfig))

            // We need to send an empty message to create the Enclave object and start it up.
            sender.send(0, Consumer { })
            stateManager.checkStateIs<Started>()
        } catch (e: Exception) {
            throw InvalidEnclaveException("Unable to start enclave", e)
        }
    }

    // TODO val info: EnclaveInstanceInfo

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
                // On start the host sends a (blank) message to the enclave to start it up. It responds by sending back
                // a boolean for whether it implements EnclaveCall or not. Upon receipt the host can flag itself as
                // fully started.
                val isEnclaveCall = input.getBoolean()
                input.checkNoRemaining()
                stateManager.state = Started(isEnclaveCall)
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
        sender.send(1 + bytes.size, Consumer { buffer ->
            buffer.putBoolean(isEnclaveCallReturn)
            buffer.put(bytes)
        })
    }

    // TODO deliverMail

    override fun close() {
        fileToDelete?.deleteQuietly()
        stateManager.state = Closed
    }

    private class HostHandler(private val host: EnclaveHost) : Handler<Sender> {
        override fun connect(upstream: Sender): Sender = upstream
        override fun onReceive(connection: Sender, input: ByteBuffer) = host.onReceive(input)
    }

    private sealed class State {
        object New : State()
        class Started(val enclaveIsEnclaveCall: Boolean) : State()
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

// TODO The enclave load mode enum should not be needed. It should be possible to query the enclave mode from the .so file
//      itself, as that's what defines the mode. https://r3-cev.atlassian.net/browse/CON-13.
//      I've introduced this as a duplicate of EnclaveLoadMode to avoid exposing it in the Conclave.
enum class EnclaveMode {
    RELEASE,
    DEBUG,
    SIMULATION
}
