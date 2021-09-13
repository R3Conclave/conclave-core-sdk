package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.*

interface EnclaveEnvironment {
    val enclaveMode: EnclaveMode

    /**
     * Create an [SgxReport] of the enclave.
     * @param targetInfo Optional information of the target enclave if the report is to be used as part of local
     * attestation. An example is during quoting when the report is sent to the Quoting Enclave for signing.
     * @param reportData Optional data to be included in the report. If null the data area of the report will be 0.
     */
    fun createReport(
        targetInfo: ByteCursor<SgxTargetInfo>?,
        reportData: ByteCursor<SgxReportData>?
    ): ByteCursor<SgxReport>

    /**
     * Fill [output] with indices in ([offset], [offset] + [length]) with random bytes.
     */
    fun randomBytes(output: ByteArray, offset: Int = 0, length: Int = output.size)

    /**
     * Encrypt and authenticate the given [PlaintextAndEnvelope] using AES-GCM. The key used is unique to the enclave.
     * This method can be used to preserve secret data after the enclave is destroyed. The sealed data blob can be
     * unsealed on future instantiations of the enclave using [unsealData], even if the platform firmware has been
     * updated.
     *
     * @param toBeSealed [PlaintextAndEnvelope] containing the plaintext to be encrypted and an optional public
     * additional data to be included in the authentication.
     * @return the sealed blob output.
     */
    fun sealData(toBeSealed: PlaintextAndEnvelope): ByteArray

    /**
     * Decrypts the given sealed data using AES-GCM so that the enclave data can be restored. This method can be used to
     * restore secret data that was preserved after an earlier instantiation of this enclave.
     * @param sealedBlob the encrypted blob to be decrypted.
     * @return A [PlaintextAndEnvelope] containing the decrypted plaintext and an optional authenticated data if the
     * sealed blob had one.
     * @see sealData
     */
    fun unsealData(sealedBlob: ByteArray): PlaintextAndEnvelope

    /**
     * Returns a 128-bit stable pseudo-random secret key based on the given [SgxKeyRequest] object.
     * @param keyRequest Object for selecting the appropriate key and any additional parameters required in the
     * derivation of that key.
     * @return 128-bit secret key.
     */
    fun getSecretKey(keyRequest: ByteCursor<SgxKeyRequest>): ByteArray

    fun getSecretKey(block: (ByteCursor<SgxKeyRequest>) -> Unit): ByteArray {
        val keyRequest = ByteCursor.allocate(SgxKeyRequest)
        block(keyRequest)
        return getSecretKey(keyRequest)
    }
}

/**
 * Unsealed data holder, used by the sealing conclave API.
 * @param plaintext unsealed text.
 * @param authenticatedData optional authenticated data.
 */
class PlaintextAndEnvelope(val plaintext: ByteArray, val authenticatedData: ByteArray? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaintextAndEnvelope) return false
        if (authenticatedData != null) {
            if (other.authenticatedData == null || !authenticatedData.contentEquals(other.authenticatedData)) {
                return false
            }
        } else if (other.authenticatedData != null) {
            return false
        }
        return plaintext.contentEquals(other.plaintext)
    }

    override fun hashCode(): Int {
        var result = plaintext.contentHashCode()
        result = 31 * result + (authenticatedData?.contentHashCode() ?: 0)
        return result
    }
}
