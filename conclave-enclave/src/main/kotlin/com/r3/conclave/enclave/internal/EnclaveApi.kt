package com.r3.conclave.enclave.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.SignatureSchemeId

interface EnclaveApi {
    /**
     * Create an SGX report.
     * @param targetInfo optional information of the target enclave if the report is to be used as part of local
     *     attestation. An example is during quoting when the report is sent to the Quoting Enclave for signing.
     *   @see SgxTargetInfo
     * @param reportData optional data to be included in the report. If null the data area of the report will be 0.
     *   @see SgxReportData
     * @param reportOut the byte array to put the report in. The size should be the return value of [SgxReport.size].
     *   @see SgxReport
     * sgx_status_t sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report)
     * TODO change this to use ByteBuffers directly
     */
    fun createReport(targetInfo: ByteArray?, reportData: ByteArray?, reportOut: ByteArray)

    /**
     * Fill [output] with indices in ([offset], [offset] + [length]) with random bytes using the `RDRAND` instruction.
     */
    fun randomBytes(output: ByteArray, offset: Int, length: Int)

    /**
     * Factory function giving access to cryptographic signature scheme implementations.
     */
    @JvmDefault
    fun getSignatureScheme(spec: SignatureSchemeId): SignatureScheme {
        return SignatureSchemeFactory.make(spec)
    }

    /**
     * Fill [output] with random bytes.
     */
    @JvmDefault
    fun randomBytes(output: ByteArray) {
        randomBytes(output, 0, output.size)
    }

    /**
     * @return true if the enclave was loaded in debug mode, i.e. its report's `DEBUG` flag is set, false otherwise.
     */
    @JvmDefault
    fun isDebugMode(): Boolean {
        val isEnclaveDebug = isEnclaveDebug
        return if (isEnclaveDebug == null) {
            val report = Cursor.allocate(SgxReport)
            createReport(null, null, report.getBuffer().array())
            val enclaveFlags = report[SgxReport.body][SgxReportBody.attributes][SgxAttributes.flags].read()
            val result = enclaveFlags and SgxEnclaveFlags.DEBUG != 0L
            EnclaveApi.isEnclaveDebug = result
            result
        } else {
            isEnclaveDebug
        }
    }

    /**
     * @return true if the enclave is a simulation enclave, false otherwise.
     */
    @JvmDefault
    fun isSimulation(): Boolean {
        return Native.isEnclaveSimulation()
    }

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
        return ByteArray(calcSealedBlobSize(toBeSealed.plaintext.size,
                toBeSealed.authenticatedData?.size ?: 0)).also {
            Native.sealData(it, 0, it.size, toBeSealed.plaintext.bytes, 0, toBeSealed.plaintext.size,
                    toBeSealed.authenticatedData?.bytes ?: null, 0, toBeSealed.authenticatedData?.size ?: 0)
        }
    }

    /**
     * Unseal the data. (Wrapper around `sgx_unseal_data`.)
     * @param sealedBlob data to be decrypted.
     * @return decrypted PlaintextAndEnvelope object.
     */
    @JvmDefault
    fun unsealData(sealedBlob: ByteArray): PlaintextAndEnvelope {
        require(sealedBlob.isNotEmpty())
        val authenticatedDataSize =  authenticatedDataSize(sealedBlob)
        return PlaintextAndEnvelope(OpaqueBytes(ByteArray(plaintextSizeFromSealedData(sealedBlob))),
                if (authenticatedDataSize > 0) OpaqueBytes(ByteArray(authenticatedDataSize)) else null).also {
            Native.unsealData(sealedBlob, 0, sealedBlob.size, it.plaintext.bytes, 0, it.plaintext.size,
                    it.authenticatedData?.bytes ?: null, 0, it.authenticatedData?.size ?: 0)
        }
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
     * Returns a 128 bit key provided by Intel's SGX hardware with default values for `keyType=SGX_KEYSELECT_SEAL`
     *  and `keyPolicy="MRSIGNER or MRENCLAVE"` and use of CPU derivation.
     * @param keyType type of key used for key derivation (check `enum class KeyType` for possible values).
     * @param useSigner true = use the enclave's SIGNER for key derivation, false = use the enclave MEASUREMENT for key derivation.
     * @param cpuSvn use of CPU for key derivation.
     * @return 128 bit key.
     */
    @JvmDefault
    fun defaultSealingKey(keyType: KeyType = KeyType.SEAL, useSigner: Boolean = true, cpuSvn: Boolean = true): ByteArray {
        return ByteArray(16).also {
            Native.sgxKey(keyType.value, if(useSigner) KeyPolicy.MRSIGNER.value else KeyPolicy.MRENCLAVE.value, cpuSvn, it, 0, it.size)
        }
    }

    private companion object {
        var isEnclaveDebug: Boolean? = null
    }
}
