package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*

interface EnclaveEnvironment {
    val enclaveMode: EnclaveMode

    /**
     * Create an SGX report.
     * @param targetInfoIn optional information of the target enclave ([SgxTargetInfo]) if the report is to be used as part of local
     *     attestation. An example is during quoting when the report is sent to the Quoting Enclave for signing.
     * @param reportDataIn optional data to be included in the report ([SgxReportData]). If null the data area of the report will be 0.
     * @param reportOut the byte array to put the report in ([SgxReport]). The size should be the return value of [SgxReport.size].
     * sgx_status_t sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report)
     * TODO change this to use ByteBuffers directly
     */
    fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray)

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
     * Returns a 128 bit stable pseudo-random key based on the per-CPU key and other pieces of data.
     * @param keyType type of key used for key derivation, defaults to [KeyType.SEAL].
     * @param useSigner true = use enclave's MRSIGNER, false = use enclave's MRENCLAVE for key derivation, defaults to `true`.
     * @return 128 bit key.
     */
    fun defaultSealingKey(keyType: KeyType = KeyType.SEAL, useSigner: Boolean = true): ByteArray
}
