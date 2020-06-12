package com.r3.conclave.common.internal

/**
 * Which type of key to derive. The CPU can calculate several different categories of keys which aren't substitutable,
 * and which are recognised by different parts of the SGX stack. You probably want `SEAL`.
 * Note that you can have individual keys within these categories that are named by index, this enum doesn't incorporate
 * that.
 */
enum class KeyType(val value: Int) {
    /**
     * Launch Key. Provided by Intel's Launch Enclave, this type of key is used by to control which enclaves are allowed
     * to be started, e.g. for Intel's whitelisting scheme.
     */
    EINITTOKEN(0x0000),

    /**
     * Provisioning key. Derived from Intel's Root Provisioning Key (RPK), used as a root of trust, it's only provided
     * by the Launch Enclave if the enclave has been signed by Intel. It's used e.g. for remote attestation.
     */
    PROVISION(0x0001),

    /**
     * Provisioning seal key. Derived from Intel's Root Provisioning Key (RPK) and Root Sealing Key (RSK), it's used to
     * encrypt the platform's private key and send it to Intel's Attestation Service.
     */
    PROVISION_SEAL(0x0002),

    /**
     * Report key. Derived from Intel's Root Sealing Key (RSK), it's used for the local attestation process.
     */
    REPORT(0x0003),

    /**
     * Seal key. Derived from Intel's Root Sealing Key (RSK), it's used to encrypt data in the current platform.
     */
    SEAL(0x0004)
}
