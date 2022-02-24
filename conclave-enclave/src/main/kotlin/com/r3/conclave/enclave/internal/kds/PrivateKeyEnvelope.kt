package com.r3.conclave.enclave.internal.kds

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.utilities.internal.getIntLengthPrefixBytes
import java.nio.ByteBuffer

class PrivateKeyEnvelope(val name: String, val masterKeyType: MasterKeyType, val policyConstraint: EnclaveConstraint) {

    companion object {
        private val masterKeyTypeValues = MasterKeyType.values()
        fun deserialize(byteArray: ByteArray): PrivateKeyEnvelope {
            val bufferBytes = ByteBuffer.wrap(byteArray)

            val name = bufferBytes.getString()
            val masterKeyTypeOrdinal = bufferBytes.get().toInt()
            val policyConstraint = bufferBytes.getString()

            return PrivateKeyEnvelope(
                name,
                masterKeyTypeValues[masterKeyTypeOrdinal],
                EnclaveConstraint.parse(policyConstraint)
            )
        }

        private fun ByteBuffer.getString(): String =
            String(getIntLengthPrefixBytes())
    }
}