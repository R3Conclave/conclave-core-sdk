package com.r3.conclave.common.internal

/**
 * You must select which policies use in key derivation. When used in e.g. `defaultSealingKey`, you can combine many
 * policies by using a bitwise `or` operation on the enumeration's values.
 */
enum class KeyPolicy(val value: Int) {
    /**
     * Derive key using the enclave’s `ENCLAVE` measurement register, which means it can only be used by that particular
     * enclave.
     */
    MRENCLAVE(0x0001),

    /**
     * Derive key using the enclave’s `SIGNER` measurement register, which means that it can only used by enclaves
     * signed by the same vendor.
     */
    MRSIGNER(0x0002),

    /**
     * Derive key without the enclave's `ISVPRODID`, which is assigned by the enclave's author to segment enclaves with
     * the same author identity. This means that the sealed data can be unsealed by any product by the same vendor.
     */
    NOISVPRODID(0x0004),

    /**
     * Derive key with the enclave's `CONFIGID`.
     */
    CONFIGID(0x0008),

    /**
     * Derive key with the enclave's `ISVFAMILYID`.
     */
    ISVFAMILYID(0x0010),

    /**
     * Derive key with the enclave's `ISVEXTPRODID`.
     */
    ISVEXTPRODID(0x0020)
}
