package com.r3.sgx.core.common.internal.encryption

/**
 * Stateful representation of shared key-agreement protocol
 */
interface KeyAgreement {
    val publicSessionKey: ByteArray
    fun computeSharedSecret(peerPublicKey: ByteArray): ByteArray
}