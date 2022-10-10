package com.r3.conclave.common

import java.time.Period
import java.time.ZonedDateTime

/**
 * This utility class provides a template against which remote attestations may be matched. It defines a little domain
 * specific language intended for use in configuration files, command line flags etc.
 *
 * A constraint is intended to match a single logical enclave. A logical enclave may be made up of multiple instances on
 * different machines, and multiple versions of the enclave software. Constraints can be as tight or as loose as you like,
 * however, it's better for security when you're more specific about what you want, and more flexible/convenient when you
 * leave it vague. The correct tradeoff depends on your app and is up to you.
 *
 * You don't have to use this class: you can check the components of a [EnclaveInstanceInfo] yourself. However these
 * checks are normally just boilerplate and thus it's convenient to use an [EnclaveConstraint].
 *
 * Every criteria must be satisfied for the constraint to be satisfied. Hashes and public keys are combined such that
 * satisfying any one of them is sufficient:
 *
 * (hash OR hash OR key) AND minRevocationLevel AND productID AND minSecurityLevel
 */
class EnclaveConstraint {
    /**
     * The set of measurement hashes that will be accepted. A match against any hash satisfies
     * this criteria and [acceptableSigners].
     *
     * At least one code hash or signer must be provided.
     */
    var acceptableCodeHashes: MutableSet<SecureHash> = HashSet()

    /**
     * The set of code signers that will be accepted. A match against any signer satisfies this
     * criteria and [acceptableCodeHashes]. A [productID] must also be specified since a single signing key can sign multiple unrelated enclaves.
     *
     * At least one code hash or signer must be provided.
     */
    var acceptableSigners: MutableSet<SecureHash> = HashSet()

    /**
     * The Product ID is a number between 0 and 65535 that differentiates products signed by the same
     * signing key. Enclaves with different product IDs cannot access each other's sealed data. You need to specify this to
     * prevent confusions between two products that speak the same protocol and which a malicious host has swapped. This must
     * be specified if there is at least one [acceptableSigners].
     */
    var productID: Int? = null
        set(value) {
            if (value != null) {
                require(value >= 0) { "Product ID is negative." }
                require(value < 65536) { "Product ID is not a 16-bit number." }
            }
            field = value
        }

    /**
     * [EnclaveInfo.revocationLevel] must be greater or equal to this. That corresponds to the
     * SGX notion of an enclave security version level. Null means accept all revocation levels (i.e. there were no revocations
     * yet). This value comes from the signed enclave metadata and cannot be forged by a hacked enclave: only if the enclave
     * developer's signing key has been compromised. The remote enclave must have a revocation level (SVN) greater than this
     * value, i.e. 1 means the enclave must have an SVN of 2 or above.
     */
    var minRevocationLevel: Int? = null
        set(value) {
            if (value != null) {
                require(value >= 0) { "Min revocation level is negative." }
            }
            field = value
        }

    /**
     * Whether to accept debug/insecure enclaves, or enclaves running on hosts that have fallen
     * behind on their security patches. By default, stale machines running old software or microcode are accepted
     * to avoid outages due to operator laxness. You can tighten this if you want to.
     *
     * N.B. Only insecure enclaves are accepted if the security level is set to insecure. This prevents clients from
     * connecting to production enclaves by mistake.
     */
    var minSecurityLevel: EnclaveSecurityInfo.Summary = EnclaveSecurityInfo.Summary.STALE

    /**
     * How old the attestation is allowed to be. Defaults to null, representative of no maximum age.
     * The Period class stores the duration in (months days weeks etc). It's important to remember that these quantities
     * mean nothing until they are compared with a date because, for instance, the number of days in a month is variable.
     */
    var maxAttestationAge: Period? = null

    /**
     * @throws IllegalStateException If any criteria are in an incorrect state. For example, [productID] not specified
     * when [acceptableSigners] has.
     */
    private fun checkCriteriaValid() {
        check(acceptableCodeHashes.isNotEmpty() || acceptableSigners.isNotEmpty()) {
            "Either a code hash or a code signer must be provided."
        }
        if (productID != null) {
            check(acceptableSigners.isNotEmpty()) { "A code signer must be provided with a product ID." }
        }
        if (acceptableSigners.isNotEmpty()) {
            check(productID != null) { "A product ID must be provided with a code signer." }
        }
    }

    /**
     * @throws InvalidEnclaveException if the constraint doesn't match the [EnclaveInfo] with a message explaining why not.
     *
     * @throws IllegalStateException If any criteria are in an incorrect state. For example, [productID] not specified
     * when [acceptableSigners] has.
     */
    @Throws(InvalidEnclaveException::class)
    fun check(enclave: EnclaveInstanceInfo) {
        // First make sure the state of the constraint is valid.
        checkCriteriaValid()

        // Now check the enclave against the constraint, throwing InvalidEnclaveException if it doesn't match.
        if (acceptableCodeHashes.isNotEmpty() && acceptableSigners.isEmpty()) {
            checkEnclave(enclave.enclaveInfo.codeHash in acceptableCodeHashes) {
                "Enclave code hash does not match any of the acceptable code hashes. (measurement hash ${enclave.enclaveInfo.codeHash} vs acceptable ${acceptableCodeHashes.joinToString()}"
            }
        } else if (acceptableCodeHashes.isEmpty() && acceptableSigners.isNotEmpty()) {
            checkEnclave(enclave.enclaveInfo.codeSigningKeyHash in acceptableSigners) {
                "Enclave code signer does not match any of the acceptable code signers. (key hash ${enclave.enclaveInfo.codeSigningKeyHash} vs acceptable ${acceptableSigners.joinToString()})"
            }
        } else {
            // Both are non-empty
            checkEnclave(enclave.enclaveInfo.codeHash in acceptableCodeHashes || enclave.enclaveInfo.codeSigningKeyHash in acceptableSigners) {
                "Enclave does not match any of the acceptable code hashes or code signers."
            }
        }

        checkEnclave(productID == null || enclave.enclaveInfo.productID == productID) {
            "Enclave has a product ID of ${enclave.enclaveInfo.productID} which does not match the criteria of ${productID}."
        }

        minRevocationLevel?.let {
            checkEnclave(enclave.enclaveInfo.revocationLevel >= it) {
                "Enclave has a revocation level of ${enclave.enclaveInfo.revocationLevel} which is lower than the required level of $it."
            }
        }

        checkSecurityLevel(enclave)

        maxAttestationAge?.let {
            val earliestAllowedAttestation = ZonedDateTime.now() - maxAttestationAge
            checkEnclave(earliestAllowedAttestation.toInstant().isBefore(enclave.securityInfo.timestamp)) {
                "Enclave attestation data is out of date with an age that exceeds ${maxAttestationAge}."
            }
        }
    }

    private fun checkSecurityLevel(enclave: EnclaveInstanceInfo) {
        // If the security level required by the client is insecure then the enclave security must be insecure. This prevents
        // clients from connecting to enclaves running in production by mistake. This also stops a malicious host from connecting
        // a client to a production enclave in order to corrupt data. Check CON-806 for more details
        if (minSecurityLevel == EnclaveSecurityInfo.Summary.INSECURE) {
            checkEnclave(enclave.securityInfo.summary == minSecurityLevel) {
                "Enclave has a security level of ${enclave.securityInfo.summary} which does not match the required level of $minSecurityLevel."
            }
        } else {
            checkEnclave(enclave.securityInfo.summary >= minSecurityLevel) {
                "Enclave has a security level of ${enclave.securityInfo.summary} which is lower than the required level of $minSecurityLevel."
            }
        }
    }

    private inline fun checkEnclave(value: Boolean, message: () -> String) {
        if (!value) {
            throw InvalidEnclaveException(message())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnclaveConstraint) return false

        if (acceptableCodeHashes != other.acceptableCodeHashes) return false
        if (acceptableSigners != other.acceptableSigners) return false
        if (productID != other.productID) return false
        if (minRevocationLevel != other.minRevocationLevel) return false
        if (minSecurityLevel != other.minSecurityLevel) return false
        if (maxAttestationAge != other.maxAttestationAge) return false

        return true
    }

    override fun hashCode(): Int {
        var result = acceptableCodeHashes.hashCode()
        result = 31 * result + acceptableSigners.hashCode()
        result = 31 * result + (productID ?: 0)
        result = 31 * result + (minRevocationLevel ?: 0)
        result = 31 * result + minSecurityLevel.hashCode()
        result = 31 * result + maxAttestationAge.hashCode()
        return result
    }

    override fun toString(): String {
        val tokens = ArrayList<String>()
        acceptableCodeHashes.mapTo(tokens) { "C:$it" }
        acceptableSigners.mapTo(tokens) { "S:$it" }
        productID?.let { tokens += "PROD:$it" }
        minRevocationLevel?.let { tokens += "REVOKE:$it" }
        maxAttestationAge?.let { tokens += "EXPIRE:$it" }
        tokens += "SEC:$minSecurityLevel"
        return tokens.joinToString(" ")
    }

    /**
     * Suppress kotlin specific companion objects from our API documentation.
     * The public items within the object are still published in the documentation.
     * @suppress
     */
    companion object {
        private val keys = setOf("C", "S", "PROD", "REVOKE", "SEC", "EXPIRE")

        /**
         * Parses a Conclave specific textual constraint format designed to be compact and conveniently embeddable in
         * config files, markup, source code etc.
         *
         * It consists of space separated tokens. Each token is a key:value pair. The following keys are defined:
         *
         * * `PROD:` the value of [productID].
         * * `C:` an entry in the [acceptableCodeHashes] set.
         * * `S:` an entry in the [acceptableSigners] set.
         * * `REVOKE:` the value of [minRevocationLevel], optional
         * * `SEC:` whether to accept debug/stale enclave hosts or not, optional.
         * * `EXPIRE:` expiry duration, check if the attestation is older than the specified duration, optional.
         *             The duration string uses the ISO-8601 duration format.
         *
         * `SEC` is optional. It may take values of `INSECURE`, `STALE` or `SECURE`. See the documentation for
         * [EnclaveSecurityInfo.Summary] for information on what these mean. The default is `STALE`, which optimises for uptime.
         *
         * An example descriptor might look like this:
         *
         * `PROD:10 S:bb53e85cb86e7f1e2b7d97620e25d8d0a250c8fdbfe9b7cddf940bd08b646c88`
         *
         * which means, accept any enclave of product ID 10 signed by the key `bb53...`
         *
         * Alternatively:
         *
         * `C:2797b9581b9377d41a8ffc45990335048e79c976a6bbb4e7692ecad699a55317 C:f96839b2159ecf8ea80cd3c1eb6be7160b05bc0d701b115b64b7e0725d15adee`
         *
         * says, accept if the code/measurement hash is either `2797...` or `f968....`
         *
         * @param descriptor String specifying enclave constraints by way of the conclave constraints DSL.
         *
         * @param checkValidity Boolean, when set to true, perform a validity check after parsing the descriptor.
         *
         * @throws IllegalArgumentException If an error occurs when parsing the descriptor.
         *
         * @throws IllegalStateException If criteria are in an incorrect state. For example, [productID] not specified
         * when [acceptableSigners] has. This only occurs if checkValidity is set to true.
         */
        @JvmStatic
        fun parse(descriptor: String, checkValidity: Boolean): EnclaveConstraint {
            val keyValues = descriptor
                .splitToSequence(' ')
                .filter { it.isNotEmpty() }
                .map {
                    val parts = it.split(':')
                    require(parts.size == 2) { "Invalid token: $it" }
                    parts
                }
                .groupBy({ it[0] }, { it[1] })

            (keyValues.keys - keys).let { require(it.isEmpty()) { "Unknown keys $it" } }

            val constraint = EnclaveConstraint()
            keyValues["C"]?.forEach {
                constraint.acceptableCodeHashes.add(SecureHash.parse(it))
            }
            keyValues["S"]?.forEach {
                constraint.acceptableSigners.add(SecureHash.parse(it))
            }
            constraint.productID = getSingleOrNone(keyValues, "PROD")?.toInt()
            constraint.minRevocationLevel = getSingleOrNone(keyValues, "REVOKE")?.toInt()
            getSingleOrNone(keyValues, "SEC")?.let {
                constraint.minSecurityLevel = EnclaveSecurityInfo.Summary.valueOf(it)
            }
            getSingleOrNone(keyValues, "EXPIRE")?.let {
                constraint.maxAttestationAge = Period.parse(it)
            }
            if (checkValidity) {
                constraint.checkCriteriaValid()
            }
            return constraint
        }

        /**
         * Parses a Conclave specific textual constraint format designed to be compact and conveniently embeddable in
         * config files, markup, source code etc.
         *
         * It consists of space separated tokens. Each token is a key:value pair. The following keys are defined:
         *
         * * `PROD:` the value of [productID].
         * * `C:` an entry in the [acceptableCodeHashes] set.
         * * `S:` an entry in the [acceptableSigners] set.
         * * `REVOKE:` the value of [minRevocationLevel], optional
         * * `SEC:` whether to accept debug/stale enclave hosts or not, optional.
         * * `EXPIRE:` expiry duration, check if the attestation is older than the specified duration, optional.
         *             The duration string uses the ISO-8601 duration format.
         *
         * `SEC` is optional. It may take values of `INSECURE`, `STALE` or `SECURE`. See the documentation for
         * [EnclaveSecurityInfo.Summary] for information on what these mean. The default is `STALE`, which optimises for uptime.
         *
         * An example descriptor might look like this:
         *
         * `PROD:10 S:bb53e85cb86e7f1e2b7d97620e25d8d0a250c8fdbfe9b7cddf940bd08b646c88`
         *
         * which means, accept any enclave of product ID 10 signed by the key `bb53...`
         *
         * Alternatively:
         *
         * `C:2797b9581b9377d41a8ffc45990335048e79c976a6bbb4e7692ecad699a55317 C:f96839b2159ecf8ea80cd3c1eb6be7160b05bc0d701b115b64b7e0725d15adee`
         *
         * says, accept if the code/measurement hash is either `2797...` or `f968....`
         *
         * @param descriptor String specifying enclave constraints by way of the conclave constraints DSL.
         *
         * @throws IllegalArgumentException If an error occurs when parsing the descriptor.
         *
         * @throws IllegalStateException If criteria are in an incorrect state. For example, [productID] not specified
         * when [acceptableSigners] has.
         */
        @JvmStatic
        fun parse(descriptor: String): EnclaveConstraint {
            return parse(descriptor, true);
        }

        private fun getSingleOrNone(keyValues: Map<String, List<String>>, key: String): String? {
            val values = keyValues[key] ?: return null
            return requireNotNull(values.singleOrNull()) { "More than one $key defined" }
        }
    }
}
