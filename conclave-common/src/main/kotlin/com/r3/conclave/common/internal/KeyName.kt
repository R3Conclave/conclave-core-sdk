package com.r3.conclave.common.internal

/**
 * Which type of key to derive. The CPU can calculate several different categories of keys which aren't substitutable,
 * and which are recognised by different parts of the SGX stack. You probably want [SEAL].
 * Note that you can have individual keys within these categories that are named by index, this enum doesn't incorporate
 * that.
 */
object KeyName : Enum16() {
    /**
     * Launch Key. Provided by Intel's Launch Enclave, this type of key is used by to control which enclaves are allowed
     * to be started, e.g. for Intel's whitelisting scheme.
     *
     * The key is only available to enclaves which have their [SgxEnclaveFlags.EINITTOKEN_KEY] flag enabled.
     */
    const val EINITTOKEN = 0x0000

    /**
     * Provisioning key. Derived from Intel's Root Provisioning Key (RPK), used as a root of trust, it's only provided
     * by the Launch Enclave if the enclave has been signed by Intel. It's used e.g. for remote attestation.
     *
     * The key is only available to enclaves which have their [SgxEnclaveFlags.PROVISION_KEY] flag enabled.
     */
    const val PROVISION = 0x0001

    /**
     * Provisioning seal key. Derived from Intel's Root Provisioning Key (RPK) and Root Sealing Key (RSK), it's used to
     * encrypt the platform's private key and send it to Intel's Attestation Service.
     *
     * The key is only available to enclaves which have their [SgxEnclaveFlags.PROVISION_KEY] flag enabled.
     */
    const val PROVISION_SEAL = 0x0002

    /**
     * Report key. Derived from Intel's Root Sealing Key (RSK), it's used for the local attestation process.
     */
    const val REPORT = 0x0003

    /**
     * The Seal key is a general purpose key for the enclave to use to protect secrets. Typical uses of the Seal key are
     * encrypting and calculating MAC of secrets on disk.
     *
     * There are two types of Seal key: [KeyPolicy.MRENCLAVE] and [KeyPolicy.MRSIGNER].
     */
    const val SEAL = 0x0004
}
