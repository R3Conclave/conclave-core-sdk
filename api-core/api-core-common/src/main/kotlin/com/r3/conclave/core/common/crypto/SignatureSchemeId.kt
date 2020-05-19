package com.r3.conclave.core.common.crypto

/**
 * Enumerate collection of predefined signature schemes avilable in the enclave API.
 *
 * @property id Internal integer identifier of the scheme
 */
enum class SignatureSchemeId(val id: Int) {
    /**
     * EdDSA signature scheme using the ed25519 twisted Edwards curve and SHA512 for message hashing.
     * The actual algorithm is PureEdDSA Ed25519 as defined in https://tools.ietf.org/html/rfc8032
     * Not to be confused with the EdDSA variants, Ed25519ctx and Ed25519ph.
     */
    EDDSA_ED25519_SHA512(0)
}