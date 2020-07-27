package com.r3.conclave.mail

import com.r3.conclave.mail.internal.noise.protocol.Noise
import java.security.*

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

    val publicKey get() = Curve25519PublicKey(Noise.createDH("25519").apply { setPrivateKey(encoded, 0) }.publicKey)
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

    override fun toString(): String = encoded.contentToString()
}

/**
 * Creates Curve25519 keys. Equivalent to what you get back from KeyPairGenerator.getInstance("X25519") on Java 11+.
 */
class Curve25519KeyPairGenerator : KeyPairGeneratorSpi() {
    @Volatile
    private var rng: SecureRandom = SecureRandom.getInstanceStrong()

    override fun generateKeyPair(): KeyPair {
        val privateKey = Curve25519PrivateKey(rng.generateSeed(32))
        return KeyPair(privateKey.publicKey, privateKey)
    }

    override fun initialize(keysize: Int, random: SecureRandom) {
        require(keysize == 256) { "keysize must be 256, was $keysize" }
        rng = random
    }
}