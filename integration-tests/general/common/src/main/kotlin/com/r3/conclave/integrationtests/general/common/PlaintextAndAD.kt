package com.r3.conclave.integrationtests.general.common

import kotlinx.serialization.Serializable

/**
 * This is a copy of the `PlaintextAndEnvelope`. However that exists in `conclave-enclave` and it's probably not worth
 * moving it in to `conclave-common` just to be able to serialise it in these integration tests.
 */
@Serializable
class PlaintextAndAD(val plaintext: ByteArray, val authenticatedData: ByteArray? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaintextAndAD) return false
        if (authenticatedData != null) {
            if (other.authenticatedData == null || !authenticatedData.contentEquals(other.authenticatedData)) {
                return false
            }
        } else if (other.authenticatedData != null) {
            return false
        }
        return plaintext.contentEquals(other.plaintext)
    }

    override fun hashCode(): Int {
        var result = plaintext.contentHashCode()
        result = 31 * result + (authenticatedData?.contentHashCode() ?: 0)
        return result
    }
}
