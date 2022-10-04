package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.host.internal.kds.KDSPrivateKeyResponse
import com.r3.conclave.utilities.internal.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

abstract class EnclaveCallInterface :  CallAcceptor<HostCallType>() {
    companion object {
        private val EMPTY_BYTE_BUFFER: ByteBuffer = ByteBuffer.wrap(ByteArray(0)).asReadOnlyBuffer()
    }

    abstract fun executeECall(callType: EnclaveCallType, parameterBuffer: ByteBuffer = EMPTY_BYTE_BUFFER): ByteBuffer?

    private fun executeECallAndCheckReturn(callType: EnclaveCallType, parameterBuffer: ByteBuffer = EMPTY_BYTE_BUFFER): ByteBuffer {
        return checkNotNull(executeECall(callType, parameterBuffer)) {
            "Missing return value from call '$callType'"
        }
    }


    /**
     * Initialises the enclave by instantiating the specified class.
     * This is not currently used in mock mode.
     */
    fun initializeEnclave(enclaveClassName: String) {
        executeECall(EnclaveCallType.INITIALISE_ENCLAVE, ByteBuffer.wrap(enclaveClassName.toByteArray(StandardCharsets.UTF_8)))
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
        executeECall(EnclaveCallType.START_ENCLAVE, sealedStateBuffer)
    }

    /**
     * Stops the enclave, calling the onShutdown hook.
     */
    fun stopEnclave() {
        executeECall(EnclaveCallType.STOP_ENCLAVE)
    }

    /**
     * Request a quote for enclave instance info from the enclave.
     */
    fun getEnclaveInstanceInfoQuote(target: ByteCursor<SgxTargetInfo>): ByteCursor<SgxSignedQuote> {
        val returnBuffer = executeECallAndCheckReturn(EnclaveCallType.GET_ENCLAVE_INSTANCE_INFO_QUOTE, target.buffer)
        return Cursor.wrap(SgxSignedQuote, returnBuffer.getRemainingBytes())
    }

    /**
     * Get the KDS persistence key specification from the enclave.
     * Returns null if no KDS key spec is present in the enclave.
     */
    fun getKdsPersistenceKeySpec(): KDSKeySpec? {
        return executeECall(EnclaveCallType.GET_KDS_PERSISTENCE_KEY_SPEC)?.let { buffer ->
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
        executeECall(EnclaveCallType.SET_KDS_PERSISTENCE_KEY, kdsResponseBuffer)
    }

    /**
     * Send a command to the enclave message handler.
     */
    fun sendMessageHandlerCommand(command: ByteBuffer) {
        executeECall(EnclaveCallType.CALL_MESSAGE_HANDLER, command)
    }
}
