package com.r3.conclave.enclave.internal.kds

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.common.InvalidEnclaveException
import com.r3.conclave.common.internal.kds.EnclaveKdsConfig
import com.r3.conclave.common.kds.KDSKeySpec
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.mail.EnclaveMail
import com.r3.conclave.utilities.internal.getIntLengthPrefixBytes
import java.nio.ByteBuffer

/**
 * Represents the parsed and unencrypted KDS private key response.
 */
class KdsPrivateKeyResponse(private val mail: EnclaveMail, val kdsEnclaveInstanceInfo: EnclaveInstanceInfo) {
    companion object {
        private val masterKeyTypeValues = MasterKeyType.values()
    }

    fun getPrivateKey(kdsConfig: EnclaveKdsConfig, expectedKeySpec: KDSKeySpec): ByteArray {
        val keySpec = deserialiseKeySpecFromEnvelope()

        // Make sure the private key is actually for the request that was made.
        require(keySpec == expectedKeySpec) {
            "KDS private key response does not match requested key spec. Expected: $expectedKeySpec. Actual: $keySpec"
        }

        // Make sure the KDS enclave providing the private key satisfies the configured KDS constraint.
        try {
            kdsConfig.kdsEnclaveConstraint.check(kdsEnclaveInstanceInfo)
        } catch (e: InvalidEnclaveException) {
            throw IllegalArgumentException("KDS private key has come from a KDS enclave which does not meet the " +
                    "requirements of this enclave", e)
        }

        // Verify KDS encryption key is the same key which encrypted the mail.
        require(kdsEnclaveInstanceInfo.encryptionKey == mail.authenticatedSender) {
            "Mail authenticated sender does not match the KDS EnclaveInstanceInfo encryption key."
        }

        // The mail body is the encoded private key.
        return mail.bodyAsBytes
    }

    private fun deserialiseKeySpecFromEnvelope(): KDSKeySpec {
        val envelope = requireNotNull(mail.envelope) {
            "Mail missing envelope containing original KDS request parameters."
        }
        val buffer = ByteBuffer.wrap(envelope)

        val serialVersion = buffer.get()
        require(serialVersion.toInt() == 1) { "Unknown format $serialVersion" }

        val name = String(buffer.getIntLengthPrefixBytes())
        val masterKeyType = masterKeyTypeValues[buffer.get().toInt()]
        val policyConstraint = String(buffer.getIntLengthPrefixBytes())

        return KDSKeySpec(name, masterKeyType, policyConstraint)
    }
}
