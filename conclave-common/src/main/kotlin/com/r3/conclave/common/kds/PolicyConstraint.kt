package com.r3.conclave.common.kds

import com.r3.conclave.common.EnclaveConstraint

open class PolicyConstraint {

    var enclaveConstraint: EnclaveConstraint = EnclaveConstraint()

    var ownCodeHash = false

    var ownCodeSigner = false

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is PolicyConstraint) return false

        return other.ownCodeHash == this.ownCodeHash
                && other.ownCodeSigner == this.ownCodeSigner
                && other.enclaveConstraint == this.enclaveConstraint
    }

    override fun hashCode(): Int {
        var result = ownCodeHash.hashCode()
        result = 31 * result + ownCodeSigner.hashCode()
        result = 31 * result + enclaveConstraint.hashCode()
        return result
    }
}