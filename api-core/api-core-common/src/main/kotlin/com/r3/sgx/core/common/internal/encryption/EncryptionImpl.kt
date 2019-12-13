package com.r3.sgx.core.common.internal.encryption

import com.r3.sgx.core.common.Sender
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.SecureRandom
import java.util.function.Consumer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private object AESGCMSettings {
    val INIT_VECTOR_SIZE = Int.SIZE_BYTES
    val CIPHER_SPEC = "AES/GCM/NoPadding"
    val CIPHER_KEY_SIZE = 128
}

class EncryptorAESGCM(val secretKeySpec: SecretKeySpec): Encryptor {
    private val cipher = Cipher.getInstance(AESGCMSettings.CIPHER_SPEC)
    private var initVector = ByteArray(AESGCMSettings.INIT_VECTOR_SIZE) { 0 }
    private var tag = 0

    init {
        tag = SecureRandom().nextInt()
        nextTag()
    }

    override val spec: AlgorithmParameters
        get() = cipher.parameters

    @Synchronized
    override fun process(input: ByteBuffer, upstream: Sender) {
        nextTag()
        val gcmParameters = GCMParameterSpec(AESGCMSettings.CIPHER_KEY_SIZE, initVector)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameters)
        val outputLength = initVector.size + cipher.getOutputSize(input.remaining())
        upstream.send(outputLength, Consumer { upstreamBuffer ->
            upstreamBuffer.put(initVector)
            cipher.doFinal(input, upstreamBuffer)
        })
    }

    private fun nextTag() {
        ++tag
        ByteBuffer.wrap(initVector).putInt(tag)
    }
}

class DecryptorAESGCM(val secretKeySpec: SecretKeySpec): Decryptor {

    private val cipher = Cipher.getInstance(AESGCMSettings.CIPHER_SPEC)

    override val spec: AlgorithmParameters get() = cipher.parameters

    @Synchronized
    override fun process(input: ByteBuffer): ByteArray {
        // Deserialize cipher initialization vector
        val iv = ByteArray(AESGCMSettings.INIT_VECTOR_SIZE) {0}.also { input.get(it) }
        val gcmParameters = GCMParameterSpec(AESGCMSettings.CIPHER_KEY_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameters)
        val resultBuffer = ByteBuffer.allocate(cipher.getOutputSize(input.remaining()))
        cipher.doFinal(input, resultBuffer)
        return resultBuffer.array()
    }
}