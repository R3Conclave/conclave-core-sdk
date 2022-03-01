package com.r3.conclave.enclave.internal.kds

import com.r3.conclave.common.EnclaveConstraint
import com.r3.conclave.common.kds.MasterKeyType
import com.r3.conclave.common.internal.kds.KDSKeySpecification
import com.r3.conclave.common.internal.kds.PolicyConstraint
import java.util.*

class KDSConfiguration(val persistenceKeySpec: KDSKeySpecification?, val kdsEnclaveConstraint: EnclaveConstraint) {

    companion object {
        internal fun loadConfiguration(properties: Properties): KDSConfiguration? {
            val kdsEnabled = properties.getProperty("kds.configurationPresent").toBoolean()
            if (!kdsEnabled) {
                // KDS is not enabled.
                // There is nothing else to do.
                return null
            }

            val kdsPersistenceKeySpecEnabled =
                properties.getProperty("kds.persistenceKeySpec.configurationPresent").toBoolean()
            var kdsPersistenceKeySpec: KDSKeySpecification? = null;

            if (kdsPersistenceKeySpecEnabled) {
                // "kds.keySpec" is know deprecated. It only exists for backwards compatibility
                val persistenceKeySpec =
                    if (checkIfKeysWithPrefixExists(
                            properties,
                            "kds.keySpec"
                        )
                    ) "kds.keySpec" else "kds.persistenceKeySpec"

                // Otherwise, assemble KDS configuration from other properties
                val masterKeyType =
                    MasterKeyType.valueOf(
                        properties.getProperty("$persistenceKeySpec.masterKeyType").toString().uppercase()
                    )
                val constraint =
                    properties.getProperty("$persistenceKeySpec.policyConstraint.constraint").toString()
                val useOwnCodeHash =
                    properties.getProperty("$persistenceKeySpec.policyConstraint.useOwnCodeHash").toBoolean()
                val useOwnCodeSignerAndProductID =
                    properties.getProperty("$persistenceKeySpec.policyConstraint.useOwnCodeSignerAndProductID")
                        .toBoolean()

                val policyConstraint = PolicyConstraint().apply {
                    enclaveConstraint = EnclaveConstraint.parse(constraint, false)
                    ownCodeHash = useOwnCodeHash
                    ownCodeSignerAndProductID = useOwnCodeSignerAndProductID
                }
                kdsPersistenceKeySpec = KDSKeySpecification(masterKeyType, policyConstraint)
            }
            val kdsEnclaveConstraint =
                EnclaveConstraint.parse(properties.getProperty("kds.kdsEnclaveConstraint").toString())
            //  In case there isn't a kds spec for persistence, we will have a KDSConfiguration with
            //  only the kdsEnclaveConstraint, kdsPersistenceKeySpec would be null
            return KDSConfiguration(kdsPersistenceKeySpec, kdsEnclaveConstraint)
        }

        private fun checkIfKeysWithPrefixExists(properties: Properties, propertyNamePrefix: String): Boolean =
            properties.any { (propertyName, _) -> propertyName.toString().startsWith(propertyNamePrefix) }
    }
}
