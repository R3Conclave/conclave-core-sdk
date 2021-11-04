package com.r3.conclave.enclave.kds

import com.r3.conclave.common.EnclaveConstraint

class PolicyConstraint {
    var enclaveConstraint: EnclaveConstraint = EnclaveConstraint()

    var ownCodeHash = false

    fun useOwnCodeHash(): PolicyConstraint {
        ownCodeHash = true
        return this
    }

    var ownCodeSignerAndProductID = false

    fun useOwnCodeSignerAndProductID(): PolicyConstraint {
        ownCodeSignerAndProductID = true
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is PolicyConstraint) return false

        return other.ownCodeHash == this.ownCodeHash
                && other.ownCodeSignerAndProductID == this.ownCodeSignerAndProductID
                && other.enclaveConstraint == this.enclaveConstraint
    }

    override fun hashCode(): Int {
        var result = ownCodeHash.hashCode()
        result = 31 * result + ownCodeSignerAndProductID.hashCode()
        result = 31 * result + enclaveConstraint.hashCode()
        return result
    }

    override fun toString(): String {
        return "PolicyConstraint(" +
                "enclaveConstraint=$enclaveConstraint," +
                " ownCodeHash=$ownCodeHash," +
                " ownCodeSignerAndProductID=$ownCodeSignerAndProductID)"
    }
}
