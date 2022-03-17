package com.r3.conclave.common.kds

import com.r3.conclave.common.EnclaveConstraint

/**
 * A KDSKeySpec defines a configuration for a specific key we want to obtain from the KDS
 *
 * @property name This is the name of the specific key we want to obtain. This is part of the identity of the key:
 * for a different name, the KDS will produce a different key.
 *
 * @property masterKeyType This is the type of the key we want to obtain from the KDS.
 *
 * @property policyConstraint This is the constraint policy of the enclave for which the KDS provides the key.
 * The enclave must satisfy this constraint
 */
class KDSKeySpec(val name: String, val masterKeyType: MasterKeyType, val policyConstraint: String) {
    init {
        try {
            EnclaveConstraint.parse(policyConstraint)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid policy constraint: ${e.message}")
        }
    }

    override fun hashCode(): Int {
        var result = 31 * name.hashCode()
        result = 31 * result + masterKeyType.hashCode()
        result = 31 * result + policyConstraint.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is KDSKeySpec) return false
        return other.name == name && other.masterKeyType == masterKeyType && other.policyConstraint == policyConstraint
    }

    override fun toString(): String {
        return "KDSKeySpec(name=$name, masterKeyType=$masterKeyType, policyConstraint=$policyConstraint)"
    }
}
