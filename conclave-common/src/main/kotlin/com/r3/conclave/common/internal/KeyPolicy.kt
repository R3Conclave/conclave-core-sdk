package com.r3.conclave.common.internal

/**
 * Flags to specify what policies to use when deriving keys of type [KeyName.SEAL].
 */
object KeyPolicy : Flags16() {
    /**
     * Derive key using the enclave’s `ENCLAVE` measurement register, which means it can only be used by that particular
     * enclave.
     */
    const val MRENCLAVE = 0x0001

    /**
     * MRSIGNER-based keys are bound to the 3 tuple (MRSIGNER, ISVPRODID, ISVSVN). These keys are available to any
     * enclave with the same MRSIGNER ([SgxReportBody.mrsigner]) and ISVPRODID ([SgxReportBody.isvProdId]) and an ISVSVN
     * ([SgxReportBody.isvSvn]) equal to or greater than the key in questions. This is valuable for allowing new versions
     * of the same software to retrieve keys created before an upgrade.
     *
     * ISVPRODID can be removed from the key derivation using [NOISVPRODID] to create a shared key between different
     * products that share the same MRSIGNER.
     */
    const val MRSIGNER = 0x0002

    /**
     * Derive key without the enclave's `ISVPRODID`, which is assigned by the enclave's author to segment enclaves with
     * the same author identity. This means that the sealed data can be unsealed by any product by the same vendor.
     *
     * @see [MRSIGNER]
     */
    // TODO Currently the SGX SDK has a bug which prevents this flag's usage: https://github.com/intel/linux-sgx/issues/578
    const val NOISVPRODID = 0x0004

    /**
     * Derive key with the enclave's `CONFIGID`.
     *
     * This is only relevant if [SgxEnclaveFlags.KSS] is enabled.
     */
    const val CONFIGID = 0x0008

    /**
     * Derive key with the enclave's `ISVFAMILYID`.
     *
     * This is only relevant if [SgxEnclaveFlags.KSS] is enabled.
     */
    const val ISVFAMILYID = 0x0010

    /**
     * Derive key with the enclave's `ISVEXTPRODID`.
     *
     * This is only relevant if [SgxEnclaveFlags.KSS] is enabled.
     */
    const val ISVEXTPRODID = 0x0020
}
