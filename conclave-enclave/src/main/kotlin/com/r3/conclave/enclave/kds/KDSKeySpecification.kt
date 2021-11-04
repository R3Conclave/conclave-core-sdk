package com.r3.conclave.enclave.kds

import com.r3.conclave.common.kds.MasterKeyType

class KDSKeySpecification(val masterKeyType: MasterKeyType, val policyConstraint: PolicyConstraint) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is KDSKeySpecification) return false

        return masterKeyType == other.masterKeyType
                && policyConstraint == other.policyConstraint
    }

    override fun hashCode(): Int {
        var result = masterKeyType.hashCode()
        result = 31 * result + policyConstraint.hashCode()
        return result
    }

    override fun toString(): String {
        return "KDSKeySpecification(masterKeyType=$masterKeyType, policyConstraint=$policyConstraint)"
    }
}
