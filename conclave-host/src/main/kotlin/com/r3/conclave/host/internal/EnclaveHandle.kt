package com.r3.conclave.host.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.host.AttestationParameters
import com.r3.conclave.host.internal.attestation.EnclaveQuoteService
import com.r3.conclave.host.internal.kds.KDSPrivateKeyResponse
import com.r3.conclave.utilities.internal.*
import java.nio.ByteBuffer

/**
 * A handle to an enclave instance.
 */
interface EnclaveHandle {
    /**
     * The mode in which the enclave is running in.
     */
    val enclaveMode: EnclaveMode

    /**
     * Object for initiating enclave calls from.
     */
    val enclaveInterface: CallInterface<EnclaveCallType, HostCallType>

    /** The name of the Enclave subclass inside the sub-JVM. */
    val enclaveClassName: String

    /**
     * For Mock mode, returns the instance of the enclave.
     * For Release, Simulation and Debug modes, throws IllegalStateException.
     */
    val mockEnclave: Any

    /**
     * The quoting service to use for quote generation & verification.
     */
    var quotingService: EnclaveQuoteService

    /**
     * Initialise the enclave.
     */
    fun initialise(attestationParameters: AttestationParameters?)

    /**
     * Destroy the enclave.
     *
     * Do not call this whilst there are non-terminated enclave threads.
     */
    fun destroy()

    /**
     * Initialises the enclave by instantiating the specified class.
     * This is not currently used in mock mode.
     */
    fun initializeEnclave(enclaveClassName: String) {
        enclaveInterface.executeOutgoingCall(EnclaveCallType.INITIALISE_ENCLAVE, ByteBuffer.wrap(enclaveClassName.toByteArray()))
    }

    /**
     * Starts the enclave, passing the sealed state blob and calling the onStartup hook.
     */
    fun startEnclave(sealedState: ByteArray?) {
        val bufferSize = nullableSize(sealedState) { it.size }
        val sealedStateBuffer = ByteBuffer.allocate(bufferSize).apply {
            putNullable(sealedState) { put(it) }
            rewind()
        }
        enclaveInterface.executeOutgoingCall(EnclaveCallType.START_ENCLAVE, sealedStateBuffer)
    }

    /**
     * Stops the enclave, calling the onShutdown hook.
     */
    fun stopEnclave() {
        enclaveInterface.executeOutgoingCall(EnclaveCallType.STOP_ENCLAVE)
    }

    /**
     * Request a quote for enclave instance info from the enclave.
     */
    fun getEnclaveInstanceInfoQuote(): ByteCursor<SgxSignedQuote> {
        val quotingEnclaveTargetInfo =  quotingService.getQuotingEnclaveInfo()
        val returnBuffer = enclaveInterface.executeOutgoingCallWithReturn(EnclaveCallType.GET_ENCLAVE_INSTANCE_INFO_QUOTE, quotingEnclaveTargetInfo.buffer)
        return Cursor.wrap(SgxSignedQuote, returnBuffer.getRemainingBytes())
    }

    /**
     * Get the KDS persistence key specification from the enclave.
     * Returns null if no KDS key spec is present in the enclave.
     */
    fun getKdsPersistenceKeySpec(): KDSKeySpec? {
        return enclaveInterface.executeOutgoingCall(EnclaveCallType.GET_KDS_PERSISTENCE_KEY_SPEC)?.let { buffer ->
            val name = buffer.getIntLengthPrefixString()
            val masterKeyType = MasterKeyType.fromID(buffer.get().toInt())
            val policyConstraint = buffer.getRemainingString()
            KDSKeySpec(name, masterKeyType, policyConstraint)
        }
    }

    /**
     * Set the KDS persistence key using the response from the KDS.
     */
    fun setKdsPersistenceKey(kdsResponse: KDSPrivateKeyResponse) {
        val kdsResponseBuffer = ByteBuffer.allocate(kdsResponse.size).apply {
            putKdsPrivateKeyResponse(kdsResponse)
        }
        enclaveInterface.executeOutgoingCall(EnclaveCallType.SET_KDS_PERSISTENCE_KEY, kdsResponseBuffer)
    }

    /**
     * Send a command to the enclave message handler.
     */
    fun sendMessageHandlerCommand(command: ByteBuffer) {
        enclaveInterface.executeOutgoingCall(EnclaveCallType.CALL_MESSAGE_HANDLER, command)
    }
}
