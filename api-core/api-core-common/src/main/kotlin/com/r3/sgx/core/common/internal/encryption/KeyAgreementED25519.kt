package com.r3.sgx.core.common.internal.encryption

import org.whispersystems.curve25519.Curve25519

/**
 * A key agreement implementation based on Curve25519/Moxie
 */
class KeyAgreementED25519 : KeyAgreement {

    private val ed25519 = Curve25519.getInstance(Curve25519.JAVA)
    val keyPair = ed25519.generateKeyPair()

    override val publicSessionKey
        get() = keyPair.publicKey

    override fun computeSharedSecret(peerPublicKey: ByteArray): ByteArray {
        return ed25519.calculateAgreement(peerPublicKey, keyPair.privateKey)
    }
}