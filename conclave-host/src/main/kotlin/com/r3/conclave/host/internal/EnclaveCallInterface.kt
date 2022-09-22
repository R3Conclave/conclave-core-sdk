package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.host.internal.kds.KDSPrivateKeyResponse
import com.r3.conclave.utilities.internal.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

abstract class EnclaveCallInterface : CallInitiator<EnclaveCallType>, CallAcceptor<HostCallType>() {
    /**
     * Initialises the enclave by instantiating the specified class.
     * This is not currently used in mock mode.
     */
    fun initializeEnclave(enclaveClassName: String) {
        initiateCall(EnclaveCallType.INITIALIZE_ENCLAVE, ByteBuffer.wrap(enclaveClassName.toByteArray(StandardCharsets.UTF_8)))
    }

    /**
     * Request a quote for enclave instance info from the enclave.
     */
    fun getEnclaveInstanceInfoQuote(target: ByteCursor<SgxTargetInfo>): ByteCursor<SgxSignedQuote> {
        val returnBuffer = initiateCall(EnclaveCallType.GET_ENCLAVE_INSTANCE_INFO_QUOTE, target.buffer)
        return Cursor.wrap(SgxSignedQuote, returnBuffer.getRemainingBytes())
    }

    /**
     * Get the KDS persistence key specification from the enclave.
     * Returns null if no KDS key spec is present in the enclave.
     */
    fun getKdsPersistenceKeySpec(): KDSKeySpec? {
        val buffer = initiateCall(EnclaveCallType.GET_KDS_PERSISTENCE_KEY_SPEC)

        if (buffer.remaining() == 0) {
            return null
        }

        val name = buffer.getIntLengthPrefixString()
        val masterKeyType = MasterKeyType.fromID(buffer.get().toInt())
        val policyConstraint = buffer.getRemainingString()
        return KDSKeySpec(name, masterKeyType, policyConstraint)
    }

    /**
     * Set the KDS persistence key using the response from the KDS.
     */
    fun setKdsPersistenceKey(kdsResponse: KDSPrivateKeyResponse) {
        val kdsResponseBuffer = ByteBuffer.allocate(kdsResponse.size).apply {
            this.putKdsPrivateKeyResponse(kdsResponse)
        }
        initiateCall(EnclaveCallType.SET_KDS_PERSISTENCE_KEY, kdsResponseBuffer)
    }
}
