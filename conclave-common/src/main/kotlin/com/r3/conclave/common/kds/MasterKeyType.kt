package com.r3.conclave.common.kds

import java.lang.IllegalArgumentException

/**
 * An enumeration of master key types the Key Derivation Service (KDS) supports when deriving keys.
 */
enum class MasterKeyType(val id: Int) {
    /**
     * A development master key is used for key derivation which is only suitable for development and testing. Release
     * enclaves cannot use this key type and the KDS will reject any such request.
     */
    DEVELOPMENT(0),
    /**
     * A cluster master key suitable for applications running in production environments.
     */
    CLUSTER(1),

    /**
     * An azure managed HSM backed key suitable for applications running in production environments.
     */
    AZURE_HSM(2);

    companion object {
        /**
         * Look up a master key type enum value by ID.
         *
         * @param id The integer ID of the master key type.
         * @throws IllegalArgumentException If the passed integer ID does not correspond to a MasterKeyType.
         * @return The master key type corresponding to the passed ID.
         */
        @JvmStatic
        fun fromID(id: Int): MasterKeyType {
            return when (id) {
                DEVELOPMENT.id -> DEVELOPMENT
                CLUSTER.id -> CLUSTER
                AZURE_HSM.id -> AZURE_HSM
                else -> throw IllegalArgumentException("Invalid master key type ID.")
            }
        }
    }
}
