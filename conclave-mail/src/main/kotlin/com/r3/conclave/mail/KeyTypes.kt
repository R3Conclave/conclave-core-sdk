package com.r3.conclave.mail

import com.r3.conclave.mail.internal.noise.protocol.Noise
import com.r3.conclave.mail.internal.privateCurve25519KeyToPublic
import com.r3.conclave.utilities.internal.toHexString
import java.security.PrivateKey
import java.security.PublicKey

// Note: we don't implement ECPrivateKey here because in Java 11+ there is a different interface
// for modern EC keys anyway.

/**
 * JCA type wrapper for Curve 25519 private keys. This is equivalent to XECPrivateKey in Java 11, but using byte arrays
 * instead of BigInteger.
 */
class Curve25519PrivateKey(private val encoded: ByteArray) : PrivateKey {
    init {
        require(encoded.size == 32) { "A Curve25519 key must be 256 bits long (32 bytes)." }
    }

    override fun getAlgorithm() = "Conclave-Curve25519"
    override fun getEncoded(): ByteArray = encoded.copyOf()
    override fun getFormat(): String = "raw"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Curve25519PrivateKey) return false
        if (!encoded.contentEquals(other.encoded)) return false
        return true
    }

    override fun hashCode(): Int = encoded.contentHashCode()

    override fun toString(): String = "Curve25519PrivateKey()"

    val publicKey: Curve25519PublicKey get() = privateCurve25519KeyToPublic(this)

    /**
     * @suppress
     */
    companion object {
        /**
         * Create a new random Curve 25519 private key.
         */
        @JvmStatic
        fun random(): Curve25519PrivateKey = Curve25519PrivateKey(ByteArray(32).also(Noise::random))
    }
}

/**
 * JCA type wrapper for Curve 25519 public keys. This is equivalent to XECPublicKey in Java 11, but using byte arrays
 * instead of BigInteger.
 */
class Curve25519PublicKey(private val encoded: ByteArray) : PublicKey {
    init {
        require(encoded.size == 32) { "A Curve25519 key must be 256 bits long (32 bytes)." }
    }

    override fun getAlgorithm() = "Conclave-Curve25519"
    override fun getEncoded(): ByteArray = encoded.copyOf()
    override fun getFormat(): String = "raw"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Curve25519PublicKey) return false
        if (!encoded.contentEquals(other.encoded)) return false
        return true
    }

    override fun hashCode(): Int = encoded.contentHashCode()

    override fun toString(): String = "Curve25519PublicKey(${encoded.toHexString()})"
}
