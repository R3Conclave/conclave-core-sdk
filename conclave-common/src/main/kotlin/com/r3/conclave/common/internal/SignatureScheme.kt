package com.r3.conclave.common.internal

import java.security.*
import java.security.spec.AlgorithmParameterSpec

/**
 * Specify some attributes of a signature algorithms
 */
data class SignatureSchemeSpec(
        val schemeCodeName: String,
        val providerName: String,
        val algorithmName: String,
        val algSpec: AlgorithmParameterSpec?,
        val keySize: Int?
)

/**
 * Cryptographic signature scheme
 */
interface SignatureScheme {

    /**
     * Specification of the underlying signature algorithm
     */
    val spec: SignatureSchemeSpec

    /**
     * Generate signature encoding as array of bytes
     */
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun sign(privateKey: PrivateKey, clearData: ByteArray): ByteArray

    /**
     * Verify signature over given clear data, throwing [SignatureException] if verification fails
     */
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun verify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray)

    /**
     * Decode private key from its encoding as byte sequence
     */
    fun decodePrivateKey(encodedKey: ByteArray): PrivateKey

    /**
     * Decode public key from its encoding as byte sequence
     */
    fun decodePublicKey(encodedKey: ByteArray): PublicKey

    /**
     * Generate a public/private keys pair for the given signature scheme
     * @param sgxKey optional seed array. If not set, a random array will be generated for the seed.
     */
    fun generateKeyPair(sgxKey: ByteArray? = null): KeyPair
}

