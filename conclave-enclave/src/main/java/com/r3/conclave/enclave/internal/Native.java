package com.r3.conclave.enclave.internal;

/**
 * The Enclave JNI. We don't use System.loadLibrary, but instead rely on our custom dlsym to find the relevant symbols.
 */
public class Native {
    /**
     * Makes an OCALL.
     * @param callTypeID When the Ocall initiates a call, this contains the host call type. If the Ocall is a return or
     *                   an exception, then it is the type of the corresponding return.
     * @param messageTypeID The message type (call/return/exception) of the message, encoded as a byte.
     * @param data The chunk of data to be passed to the ocall.
     */
    public static native void jvmOcall(short callTypeID, byte messageTypeID, byte[] data);

    /**
     * Thin JNI wrapper around `sgx_create_report`.
     * @param targetInfo The bytes of an optional [SgxTargetInfo] object.
     * @param reportData The bytes of an optional [SgxReportData] object.
     * @param reportOut Output buffer of at least size [SgxReport] for receiving the cryptographic report of the enclve.
     */
    public static native void createReport(byte[] targetInfo, byte[] reportData, byte[] reportOut);

    /**
     * Returns whether the enclave is a simulation one.
     */
    public static native boolean isEnclaveSimulation();

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
    public static native void sealData(
            byte[] output,
            int outputOffset,
            int outputSize,
            byte[] plaintext,
            int plaintextOffset,
            int plaintextSize,
            byte[] authenticatedData,
            int authenticatedDataOffset,
            int authenticatedDataSize
    );

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
    public static native void unsealData(
            byte[] sealedBlob,
            int sealedBlobOffset,
            int sealedBlobLength,
            byte[] dataOut,
            int dataOutOffset,
            int dataOutLength,
            byte[] authenticatedDataOut,
            int authenticatedDataOutOffset,
            int authenticatedDataOutLength
    );

    /**
     * Returns the necessary sealed blob size (wrapper of `sgx_calc_sealed_data_size`).
     * @param plaintextSize size of data to be decrypted.
     * @param authenticatedDataSize MAC size.
     */
    public static native int calcSealedBlobSize(int plaintextSize, int authenticatedDataSize);

    /**
     * Get the MAC size (wrapper of sgx_get_add_authenticatedData_txt_len).
     * @param sealedBlob the encrypted blob.
     * @return the size of the authenticated data (sgx's MAC) stored in the blob.
     */
    public static native int authenticatedDataSize(byte[] sealedBlob);

    /**
     * Get the sealed data size (wrapper of sgx_get_encrypt_txt_len).
     * @param sealedBlob the encrypted blob.
     * @return the size of the encrypted data stored in the blob.
     */
    public static native int plaintextSizeFromSealedData(byte[] sealedBlob);

    /**
     * Thin JNI wrapper around `sgx_get_key`.
     * @param keyRequestIn The bytes of a [SgxKeyRequest] object used for selecting the appropriate key and any
     * additional parameters required in the derivation of that key.
     * @param keyOut Output buffer of at least size [SgxKey128Bit] for receiving the cryptographic key output.
     */
    public static native void getKey(byte[] keyRequestIn, byte[] keyOut);

    /**
     * JNI function (implemented in api.cpp) to pass the encryption key and
     * the filesystem sizes to FatFs encryption layer.
     * @param inMemoryFsSize Size (bytes) of the in-memory filesystem.
     * @param persistentFsSize Size (bytes) of the persistent encrypted filesystem.
     * @param inMemoryMountPath Mount point of the in-memory filesystem.
     * @param persistentMountPath Mount point of the persistent filesystem.
     * @param encryptionKey Byte array of the encryption key.
     */
    public static native void setupFileSystems(
            long inMemoryFsSize,
            long persistentFsSize,
            String inMemoryMountPath,
            String persistentMountPath,
            byte[] encryptionKey
    );
}
