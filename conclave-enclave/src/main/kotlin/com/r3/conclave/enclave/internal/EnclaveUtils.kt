package com.r3.conclave.enclave.internal

import com.r3.conclave.utilities.internal.getBytes
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EnclaveUtils {
    companion object {
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 16

        private val secureRandom = SecureRandom()

        private val cipher by lazy(LazyThreadSafetyMode.NONE) { Cipher.getInstance("AES/GCM/NoPadding") }

        fun aesEncrypt(secretKey: ByteArray, toBeSealed: PlaintextAndEnvelope): ByteArray {
            val keySpec = SecretKeySpec(secretKey, "AES")
            val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE * 8, iv))
            val sealedBlob = ByteBuffer.allocate(
                    1 + IV_SIZE + Int.SIZE_BYTES + (toBeSealed.authenticatedData?.size ?: 0) + toBeSealed.plaintext.size + TAG_SIZE
            )
            val version: Byte = 1
            sealedBlob.put(version)
            sealedBlob.put(iv)
            if (toBeSealed.authenticatedData != null) {
                cipher.updateAAD(toBeSealed.authenticatedData)
                sealedBlob.putInt(toBeSealed.authenticatedData.size)
                sealedBlob.put(toBeSealed.authenticatedData)
            } else {
                sealedBlob.putInt(0)
            }
            cipher.doFinal(ByteBuffer.wrap(toBeSealed.plaintext), sealedBlob)
            return sealedBlob.array()
        }

        fun aesDecrypt(secretKey: ByteArray, sealedBlob: ByteArray): PlaintextAndEnvelope {
            val version = sealedBlob[0]
            val expectedVersion: Byte = 1
            if (version != expectedVersion) {
                throw IllegalStateException("Unexpected sealed state version. Version on sealed state is $version and was expected to be $expectedVersion.")
            }
            val keySpec = SecretKeySpec(secretKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_SIZE * 8, sealedBlob, 1, IV_SIZE))
            val inputBuffer = ByteBuffer.wrap(sealedBlob, 1 + IV_SIZE, sealedBlob.size - IV_SIZE - 1)
            val authenticatedDataSize = inputBuffer.getInt()
            val authenticatedData = if (authenticatedDataSize > 0) {
                inputBuffer.getBytes(authenticatedDataSize).also(cipher::updateAAD)
            } else {
                null
            }
            val plaintext = ByteBuffer.allocate(inputBuffer.remaining() - TAG_SIZE)
            cipher.doFinal(inputBuffer, plaintext)
            return PlaintextAndEnvelope(plaintext.array(), authenticatedData)
        }
    }
}