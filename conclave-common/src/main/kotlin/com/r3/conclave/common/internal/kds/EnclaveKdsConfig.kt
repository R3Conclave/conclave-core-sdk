package com.r3.conclave.common.internal.kds

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType
import java.util.*

/**
 * Represents the KDS configuration for the enclave, as specified in its build.gradle.
 */
data class EnclaveKdsConfig(val kdsEnclaveConstraint: EnclaveConstraint, val persistenceKeySpec: PersistenceKeySpec?) {
    companion object {
        fun loadConfiguration(properties: Properties): EnclaveKdsConfig? {
            val kdsEnabled = properties.getProperty("kds.configurationPresent").toBoolean()
            if (!kdsEnabled) {
                // KDS is not enabled.
                // There is nothing else to do.
                return null
            }

            val kdsPersistenceKeySpecEnabled =
                properties.getProperty("kds.persistenceKeySpec.configurationPresent").toBoolean()
            var kdsPersistenceKeySpec: PersistenceKeySpec? = null

            if (kdsPersistenceKeySpecEnabled) {
                // "kds.keySpec" is know deprecated. It only exists for backwards compatibility
                val persistenceKeySpec = if (checkIfOldKeySpecIsUsed(properties)) "kds.keySpec" else "kds.persistenceKeySpec"

                // Otherwise, assemble KDS configuration from other properties
                val masterKeyType =
                    MasterKeyType.valueOf(properties.getProperty("$persistenceKeySpec.masterKeyType").uppercase())
                val constraint = properties.getProperty("$persistenceKeySpec.policyConstraint.constraint")
                val useOwnCodeHash =
                    properties.getProperty("$persistenceKeySpec.policyConstraint.useOwnCodeHash").toBoolean()
                val useOwnCodeSignerAndProductID =
                    properties.getProperty("$persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID")
                        .toBoolean()

                val policyConstraintConfig = PolicyConstraint(
                    constraint,
                    useOwnCodeHash,
                    useOwnCodeSignerAndProductID
                )
                kdsPersistenceKeySpec = PersistenceKeySpec(masterKeyType, policyConstraintConfig)
            }
            val kdsEnclaveConstraint = EnclaveConstraint.parse(properties.getProperty("kds.kdsEnclaveConstraint"))
            //  In case there isn't a kds spec for persistence, we will have a KDSConfiguration with
            //  only the kdsEnclaveConstraint, kdsPersistenceKeySpec would be null
            return EnclaveKdsConfig(kdsEnclaveConstraint, kdsPersistenceKeySpec)
        }

        private fun checkIfOldKeySpecIsUsed(properties: Properties): Boolean {
            return properties.any { (propertyName, _) -> propertyName.toString().startsWith("kds.keySpec") }
        }
    }

    data class PersistenceKeySpec(val masterKeyType: MasterKeyType, val policyConstraint: PolicyConstraint)

    data class PolicyConstraint(
        val constraint: String,
        val useOwnCodeHash: Boolean = false,
        val useOwnCodeSignerAndProductID: Boolean = false
    )
}
