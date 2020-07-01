package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SignatureSchemeId

interface EnclaveEnvironment {
    /**
     * Create an SGX report.
     * @param targetInfoIn optional information of the target enclave if the report is to be used as part of local
     *     attestation. An example is during quoting when the report is sent to the Quoting Enclave for signing.
     *   @see SgxTargetInfo
     * @param reportDataIn optional data to be included in the report. If null the data area of the report will be 0.
     *   @see SgxReportData
     * @param reportOut the byte array to put the report in. The size should be the return value of [SgxReport.size].
     *   @see SgxReport
     * sgx_status_t sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report)
     * TODO change this to use ByteBuffers directly
     */
    fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray)

    /**
     * Fill [output] with indices in ([offset], [offset] + [length]) with random bytes using the `RDRAND` instruction.
     */
    fun randomBytes(output: ByteArray, offset: Int, length: Int)

    /**
     * Factory function giving access to cryptographic signature scheme implementations.
     */
    @JvmDefault
    fun getSignatureScheme(spec: SignatureSchemeId): SignatureScheme = SignatureSchemeFactory.make(spec)

    /**
     * Fill [output] with random bytes.
     */
    @JvmDefault
    fun randomBytes(output: ByteArray) {
        randomBytes(output, 0, output.size)
    }

    val enclaveMode: EnclaveMode

    /** @see Native.calcSealedBlobSize */
    @JvmDefault
    fun calcSealedBlobSize(plaintextSize: Int, authenticatedDataSize: Int = 0): Int {
        return Native.calcSealedBlobSize(authenticatedDataSize, plaintextSize)
    }

    /**
     * Calculate the required size of the byte array to store sealed data.
     * @param plaintext text to be encrypted.
     * @param authenticatedData (optional).
     * @return the size of the byte array required to store the sealed data.
     */
    @JvmDefault
    fun calcSealedBlobSize(plaintext: ByteArray, authenticatedData: ByteArray?): Int {
        require(plaintext.isNotEmpty())
        return calcSealedBlobSize(plaintext.size, authenticatedData?.size ?: 0)
    }

    /**
     * Seal the data. (Wrapper around `sgx_seal_data`.)
     * @param toBeSealed object containing the data to be sealed.
     * @return the sealed blob output.
     */
    @JvmDefault
    fun sealData(toBeSealed: PlaintextAndEnvelope): ByteArray {
        require(toBeSealed.plaintext.size > 0)
        val sealedData = ByteArray(calcSealedBlobSize(toBeSealed.plaintext.size, toBeSealed.authenticatedData?.size ?: 0))
        Native.sealData(
                output = sealedData,
                outputOffset = 0,
                outputSize = sealedData.size,
                plaintext = toBeSealed.plaintext.bytes,
                plaintextOffset = 0,
                plaintextSize = toBeSealed.plaintext.size,
                authenticatedData = toBeSealed.authenticatedData?.bytes,
                authenticatedDataOffset = 0,
                authenticatedDataSize = toBeSealed.authenticatedData?.size ?: 0
        )
        return sealedData
    }

    /**
     * Unseal the data. (Wrapper around `sgx_unseal_data`.)
     * @param sealedBlob data to be decrypted.
     * @return decrypted PlaintextAndEnvelope object.
     */
    @JvmDefault
    fun unsealData(sealedBlob: ByteArray): PlaintextAndEnvelope {
        require(sealedBlob.isNotEmpty())
        val plaintext = ByteArray(plaintextSizeFromSealedData(sealedBlob))

        val authenticatedDataSize =  authenticatedDataSize(sealedBlob)
        val authenticatedData: ByteArray? = if (authenticatedDataSize > 0) ByteArray(authenticatedDataSize)
        else null

        Native.unsealData(
                sealedBlob = sealedBlob,
                sealedBlobOffset = 0,
                sealedBlobLength = sealedBlob.size,
                dataOut = plaintext,
                dataOutOffset = 0,
                dataOutLength = plaintext.size,
                authenticatedDataOut = authenticatedData,
                authenticatedDataOutOffset = 0,
                authenticatedDataOutLength = authenticatedData?.size ?: 0
        )

        return PlaintextAndEnvelope(OpaqueBytes(plaintext), authenticatedData?.let(::OpaqueBytes))
    }

    /** @see Native.authenticatedDataSize */
    @JvmDefault
    fun authenticatedDataSize(sealedBlob: ByteArray): Int {
        require(sealedBlob.isNotEmpty())
        return Native.authenticatedDataSize(sealedBlob)
    }

    /** @see Native.plaintextSizeFromSealedData */
    @JvmDefault
    fun plaintextSizeFromSealedData(sealedBlob: ByteArray): Int {
        require(sealedBlob.isNotEmpty())
        return Native.plaintextSizeFromSealedData(sealedBlob)
    }

    /**
     * Returns a 128 bit key provided by Intel's SGX hardware with default values for `keyType=KeyType.SEAL`
     *  and `useSigner=true`.
     * @param keyType type of key used for key derivation (check `enum class KeyType` for possible values).
     * @param useSigner true = use enclave's MRSIGNER, false = use enclave's MRENCLAVE for key derivation.
     * @return 128 bit key.
     */
    @JvmDefault
    fun defaultSealingKey(keyType: KeyType = KeyType.SEAL, useSigner: Boolean = true): ByteArray {
        return ByteArray(16).also {
            Native.sgxKey(
                    keyType = keyType.value,
                    keyPolicy = if (useSigner) KeyPolicy.MRSIGNER.value else KeyPolicy.MRENCLAVE.value,
                    keyOut = it,
                    keyOutOffset = 0,
                    keyOutLength = it.size
            )
        }
    }
}
