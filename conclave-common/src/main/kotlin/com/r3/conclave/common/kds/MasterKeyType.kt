package com.r3.conclave.common.kds

/**
 * An enumeration of master key types the Key Derivation Service (KDS) supports when deriving keys.
 */
enum class MasterKeyType {
    /**
     * A debug master key is used for key derivation which is only suitable for development and testing. Release
     * enclaves cannot use this key type and the KDS will reject any such request.
     */
    DEBUG,
    /**
     * A cluster master key suitable for applications running in production environments.
     */
    CLUSTER
}
