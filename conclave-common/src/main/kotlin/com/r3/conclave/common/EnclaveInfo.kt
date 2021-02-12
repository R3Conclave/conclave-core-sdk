package com.r3.conclave.common

/**
 * An EnclaveInfo consists of the hash of an enclave, as computed by the platform specific measurement algorithms,
 * the public key that signed the enclave, a version number chosen by the enclave author and the mode the enclave runs in.
 *
 * You would not normally create your own [EnclaveInfo]. Instead you get one from other platform classes. However, you
 * can create one if you wish to use it as a convenient holder of data.
 *
 * @property codeHash The hash reported in a remote attestation. Note that the hash isn't a simple hash of the enclave
 * file and must be calculated by special platform-specific code, for instance by the Conclave Gradle plugin. This hash
 * is sometimes called a measurement.
 *
 * @property codeSigningKeyHash The hash of the public key that signed the enclave. Usually you'll prefer to check this
 * key rather than the code hash, to allow for upgrades.
 *
 * @property productID A product ID is an unsigned 16 bit number that identifies different enclave products/lineages signed
 * by the same key. Enclaves with different product IDs cannot read each others sealed data, so it acts as a form of sandbox
 * or enclave firewall between products produced by the same vendor.
 *
 * @property revocationLevel The revocation level is incremented by an enclave author when a weakness in the enclave code
 * is fixed; doing this will enable clients to avoid connecting to old, compromised enclaves. Revocation levels should
 * not be incremented on every new release, but only when security improvements have been made.
 *
 * Note that this is not the SGX notion of a "CPU SVN", but rather the enclave-specific security version number. We call
 * it revocationLevel here to make it clearer what this actually does.
 *
 * @property enclaveMode The mode the enclave runs in, which is either release, debug or simulation. Only release mode
 * provides enclave security. The other two are only for testing and development purposes.
 */
class EnclaveInfo(
    val codeHash: SecureHash,
    val codeSigningKeyHash: SecureHash,
    val productID: Int,
    val revocationLevel: Int,
    val enclaveMode: EnclaveMode
) {
    init {
        require(productID >= 0 && productID <= 65535) { "Product ID not an unsigned 16 bit number: $productID" }
        require(revocationLevel >= 0 && revocationLevel <= 65534) { "Invalid Revocation level: $revocationLevel" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnclaveInfo) return false

        if (codeHash != other.codeHash) return false
        if (codeSigningKeyHash != other.codeSigningKeyHash) return false
        if (productID != other.productID) return false
        if (revocationLevel != other.revocationLevel) return false
        if (enclaveMode != other.enclaveMode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = codeHash.hashCode()
        result = 31 * result + codeSigningKeyHash.hashCode()
        result = 31 * result + productID
        result = 31 * result + revocationLevel
        result = 31 * result + enclaveMode.hashCode()
        return result
    }

    override fun toString(): String {
        return "EnclaveInfo(codeHash=$codeHash, codeSigningKeyHash=$codeSigningKeyHash, productID=$productID, " +
                "revocationLevel=$revocationLevel, enclaveMode=$enclaveMode)"
    }
}
