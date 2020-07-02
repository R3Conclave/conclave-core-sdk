package com.r3.conclave.mail.internal

import com.r3.conclave.mail.internal.noise.protocol.Noise
import java.security.*

// Note: we don't implement ECPrivateKey here because in Java 11+ there is a different interface
// for modern EC keys anyway.

/**
 * JCA type wrapper for Curve 25519 private keys. These are just random numbers, they are not stored
 * in "clamped" form.
 */
internal class Curve25519PrivateKey(private val encoded: ByteArray) : PrivateKey {
    override fun getAlgorithm() = "Conclave-Curve25519"
    override fun getEncoded(): ByteArray = encoded
    override fun getFormat(): String = "raw"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Curve25519PrivateKey) return false
        if (!encoded.contentEquals(other.encoded)) return false
        return true
    }

    override fun hashCode(): Int = encoded.contentHashCode()

    override fun toString(): String = "Curve25519PrivateKey()"
}

/**
 * JCA type wrapper for Curve 25519 public keys. Note that Java 11 has integrated support for
 * Curve25519 in JCA, and if/when we can take a hard dependency on Java 11 these classes could
 * be deleted.
 */
internal class Curve25519PublicKey(private val encoded: ByteArray) : PublicKey {
    override fun getAlgorithm() = "Conclave-Curve25519"
    override fun getEncoded(): ByteArray = encoded
    override fun getFormat(): String = "raw"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Curve25519PublicKey) return false
        if (!encoded.contentEquals(other.encoded)) return false
        return true
    }

    override fun hashCode(): Int = encoded.contentHashCode()

    override fun toString(): String = "Curve25519PublicKey(encoded=${encoded.contentToString()})"
}

/**
 * Creates Curve25519 keys. Note: not wired up via JCA registration, thus not available
 * to applications at this time. We don't want to expose any of this as public API because
 * we'd like to remove it after Java 11 and because we want users doing high level crypto
 * operations like using Mail, not trying to use Curve25519 directly.
 *
 * Public API is all just the generic JCA types. This class is here for testing.
 */
internal class Curve25519KeyPairGenerator : KeyPairGeneratorSpi() {
    @Volatile
    private var rng: SecureRandom = SecureRandom.getInstanceStrong()

    override fun generateKeyPair(): KeyPair {
        val p: ByteArray = rng.generateSeed(32)
        val dh = Noise.createDH("25519")
        dh.setPrivateKey(p, 0)
        return KeyPair(Curve25519PublicKey(dh.publicKey), Curve25519PrivateKey(p))
    }

    override fun initialize(keysize: Int, random: SecureRandom) {
        require(keysize == 256) { "keysize must be 256, was $keysize" }
        rng = random
    }
}