package com.r3.conclave.common.internal

import net.i2p.crypto.eddsa.*
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.*
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

/**
 * A wrapper of EdEDSA signature scheme library to run EdDSA in enclave
 */
class SignatureSchemeEdDSA(
        private val randomnessSource: SecureRandom = SecureRandom()
) : SignatureScheme {
    companion object {
        private val securityProvider = EdDSASecurityProvider()

        fun createSignature(): Signature = Signature.getInstance(EdDSAEngine.SIGNATURE_ALGORITHM, securityProvider)
    }

    override val spec = SignatureSchemeSpec(
            "EDDSA_ED25519_SHA512",
            EdDSASecurityProvider.PROVIDER_NAME,
            EdDSAEngine.SIGNATURE_ALGORITHM,
            EdDSANamedCurveTable.getByName("ED25519"),
            256)
    private val params: EdDSANamedCurveSpec

    init {
        require(spec.algorithmName == EdDSAEngine.SIGNATURE_ALGORITHM) {
            "Input spec with wrong algorithm identifier"
        }
        params = spec.algSpec as EdDSANamedCurveSpec
    }

    override fun sign(privateKey: PrivateKey, clearData: ByteArray): ByteArray {
        require(clearData.isNotEmpty()) { "Signing of an empty array is not permitted!" }
        val signature = createSignature()
        signature.initSign(privateKey)
        signature.update(clearData)
        return signature.sign()
    }

    override fun verify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray) {
        if (signatureData.isEmpty()) throw IllegalArgumentException("Signature data is empty!")
        if (clearData.isEmpty()) throw IllegalArgumentException("Clear data is empty, nothing to check!")
        val signature = createSignature()
        signature.initVerify(publicKey)
        signature.update(clearData)
        if (!signature.verify(signatureData)) {
            throw SignatureException("Signature Verification failed!")
        }
    }

    override fun generateKeyPair(seed: ByteArray?): KeyPair {
        val seedSize = params.curve.field.getb() / 8
        val privateKeySpec = EdDSAPrivateKeySpec(
                if (seed != null) {
                    require(seed.size == seedSize)
                    seed
                } else {
                    ByteArray(seedSize).also(randomnessSource::nextBytes)
                },
                params
        )
        val publicKeySpec = EdDSAPublicKeySpec(privateKeySpec.a, params)
        return KeyPair(EdDSAPublicKey(publicKeySpec), EdDSAPrivateKey(privateKeySpec))
    }

    override fun decodePrivateKey(encodedKey: ByteArray): PrivateKey {
        val keyFactory = KeyFactory.getInstance(EdDSAKey.KEY_ALGORITHM, securityProvider)
        return keyFactory.generatePrivate(X509EncodedKeySpec(encodedKey))
    }

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        val keyFactory = KeyFactory.getInstance(EdDSAKey.KEY_ALGORITHM, securityProvider)
        return keyFactory.generatePublic(X509EncodedKeySpec(encodedKey))
    }
}
