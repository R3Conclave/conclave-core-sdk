package com.r3.conclave.enclave.internal

/**
 * The Enclave JNI. We don't use System.loadLibrary, but instead rely on our custom dlsym to find the relevant symbols.
 */
object Native {

    /** Reads a chunk from the statically linked app jar. Poor man's getResources */
    external fun readAppJarChunk(srcOffset: Long, dest: ByteArray, destOffset: Int, length: Int): Int

    /**
     * Makes an OCALL.
     * @param data The chunk of data to be passed to the ocall.
     */
    external fun jvmOcall(data: ByteArray)

    /**
     * A wrapper of sgx_create_report.
     * sgx_status_t sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report)
     */
    external fun createReport(targetInfo: ByteArray?, reportData: ByteArray?, reportOut: ByteArray)

    /**
     * A wrapper of sgx_read_rand.
     */
    external fun randomBytes(output: ByteArray, offset: Int, length: Int)

    /**
     * Returns whether the enclave is a simulation one.
     */
    external fun isEnclaveSimulation(): Boolean

    /**
     * Seal the data (wrapper of sgx_seal_data).
     * @param output sealed blob output, must be pre-allocated by the caller (see [calcSealedBlobSize]).
     * @param outputOffset sealed blob offset.
     * @param outputSize sealed blob size (= [output] size minus [outputOffset]).
     * @param plaintext data to be encrypted.
     * @param plaintextOffset data offset to be encrypted.
     * @param plaintextSize size of the authenticated data (sgx's MAC) data.
     * @param authenticatedData authenticated data (sgx's MAC) to be encrypted (optional).
     * @param authenticatedDataOffset authenticated data (sgx's MAC) offset.
     * @param authenticatedDataSize size of the authenticated data (sgx's MAC).
     */
    external fun sealData(
            output: ByteArray,
            outputOffset: Int,
            outputSize: Int,
            plaintext: ByteArray,
            plaintextOffset: Int,
            plaintextSize: Int,
            authenticatedData: ByteArray?,
            authenticatedDataOffset: Int,
            authenticatedDataSize: Int
    )

    /**
     * Unseal the data (wrapper around sgx_unseal_data).
     * @param sealedBlob sealed blob to be decrypted.
     * @param sealedBlobOffset offset of the sealed blob.
     * @param sealedBlobLength size of the sealed blob.
     * @param dataOut unsealed data output, must be pre-allocated by the caller (see [plaintextSizeFromSealedData])
     * @param dataOutOffset [dataOut] offset.
     * @param dataOutLength data output size  (= [dataOut] size minus [dataOutOffset]).
     * @param authenticatedDataOut authenticated data output (sgx's MAC), must be pre-allocated (use [authenticatedDataSize]).
     * @param authenticatedDataOutOffset MAC offset.
     * @param authenticatedDataOutLength size of the authenticatedData ([authenticatedDataOut]).
     */
    external fun unsealData(
            sealedBlob: ByteArray,
            sealedBlobOffset: Int,
            sealedBlobLength: Int,
            dataOut: ByteArray,
            dataOutOffset: Int,
            dataOutLength: Int,
            authenticatedDataOut: ByteArray?,
            authenticatedDataOutOffset: Int,
            authenticatedDataOutLength: Int
    )

    /**
     * Returns the necessary sealed blob size (wrapper of `sgx_calc_sealed_data_size`).
     * @param plaintextSize size of data to be decrypted.
     * @param authenticatedDataSize MAC size.
     */
    external fun calcSealedBlobSize(plaintextSize: Int, authenticatedDataSize: Int): Int

    /**
     * Get the MAC size (wrapper of sgx_get_add_authenticatedData_txt_len).
     * @param sealedBlob the encrypted blob.
     * @return the size of the authenticated data (sgx's MAC) stored in the blob.
     */
    external fun authenticatedDataSize(sealedBlob: ByteArray): Int

    /**
     * Get the sealed data size (wrapper of sgx_get_encrypt_txt_len).
     * @param sealedBlob the encrypted blob.
     * @return the size of the encrypted data stored in the blob.
     */
    external fun plaintextSizeFromSealedData(sealedBlob: ByteArray): Int

    /**
     * Get the requested SGX key (wrapper of sgx_get_key).
     * @param keyType (check `enum class KeyType` for possible values).
     * @param keyPolicy (one or a combination using bitwise `or` of KeyPolicies).
     * @param keyOut output byte array. Must be pre-allocated to store at least 128 bits (16 bytes).
     * @param keyOutOffset optional keyOut offset.
     * @param keyOutLength size of the array to store the key (keyOut.size - keyOutOffset)
     */
    external fun sgxKey(keyType: Int, keyPolicy: Int, keyOut: ByteArray, keyOutOffset: Int, keyOutLength: Int)
}
