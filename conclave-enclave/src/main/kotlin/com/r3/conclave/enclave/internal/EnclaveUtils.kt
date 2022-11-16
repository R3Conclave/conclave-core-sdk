package com.r3.conclave.enclave.internal

import com.r3.conclave.utilities.internal.getBytes
import com.r3.conclave.utilities.internal.putIntLengthPrefixBytes
import java.nio.ByteBuffer
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EnclaveUtils {
    private const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BYTES = 16
    private const val TAG_SIZE_BITS = TAG_SIZE_BYTES * 8

    private val secureRandom = SecureRandom()

    /**
     * Encrypt and authenticate the given [PlaintextAndEnvelope] using AES-GCM with the given AES key. The resulting
     * ciphertext can be decrypted using [unsealData] with the same key.
     *
     * This method is thread-safe and can be called concurrently from multiple threads.
     */
    fun sealData(aesKey: ByteArray, toBeSealed: PlaintextAndEnvelope): ByteArray {
        val cipher = newAesGcmCipher()
        val iv = ByteArray(IV_SIZE_BYTES).also(secureRandom::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, AesKey(aesKey), GCMParameterSpec(TAG_SIZE_BITS, iv))
        val sealedBlob = ByteBuffer.allocate(1 + IV_SIZE_BYTES + Int.SIZE_BYTES +
                (toBeSealed.authenticatedData?.size ?: 0) + toBeSealed.plaintext.size + TAG_SIZE_BYTES
        )
        sealedBlob.put(1)  // Sealed blob version
        sealedBlob.put(iv)
        if (toBeSealed.authenticatedData != null) {
            cipher.updateAAD(toBeSealed.authenticatedData)
            sealedBlob.putIntLengthPrefixBytes(toBeSealed.authenticatedData)
        } else {
            sealedBlob.putInt(0)
        }
        cipher.doFinal(ByteBuffer.wrap(toBeSealed.plaintext), sealedBlob)
        return sealedBlob.array()
    }

    /**
     * Decrypt the given encrypted blob and also authenticate it using AES-GCM with the given AES key, returning a
     * [PlaintextAndEnvelope] with the plaintext and an optional authenticated data. All the remaining bytes of the
     * sealed blob buffer are decrypted. After this operation the byte buffer position is at the limit
     * (i.e. it has no remaining bytes).
     *
     * This method is thread-safe and can be called concurrently from multiple threads.
     */
    fun unsealData(aesKey: ByteArray, sealedBlob: ByteBuffer): PlaintextAndEnvelope {
        val version = sealedBlob.get().toInt()
        require(version == 1) { "Unsupported sealed blob version $version" }
        val cipher = newAesGcmCipher()
        val iv = sealedBlob.getBytes(IV_SIZE_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, AesKey(aesKey), GCMParameterSpec(TAG_SIZE_BITS, iv))
        val authenticatedDataSize = sealedBlob.getInt()
        val authenticatedData = if (authenticatedDataSize > 0) {
            sealedBlob.getBytes(authenticatedDataSize).also(cipher::updateAAD)
        } else {
            null
        }
        val plaintext = ByteBuffer.allocate(sealedBlob.remaining() - TAG_SIZE_BYTES)
        cipher.doFinal(sealedBlob, plaintext)
        return PlaintextAndEnvelope(plaintext.array(), authenticatedData)
    }

    private fun newAesGcmCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    /**
     * An alternative implementation to [SecretKeySpec] which avoids copying the input key bytes.
     */
    private class AesKey(private val keyBytes: ByteArray) : Key {
        override fun getAlgorithm(): String = "AES"
        override fun getFormat(): String = "RAW"
        override fun getEncoded(): ByteArray = keyBytes.clone()
    }
}
