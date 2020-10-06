package com.r3.conclave.common.internal

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

// see Intel's docs for ECDSA signature structure
// page 47: Quote Format
// https://download.01.org/intel-sgx/latest/dcap-latest/linux/docs/Intel_SGX_ECDSA_QuoteLibReference_DCAP_API.pdf
// TODO This can be better represented using the Cursor/Encoder API
class ECDSASignature(private val ecdsaData: ByteArray) {
    companion object {
        // https://stackoverflow.com/questions/30445997/loading-raw-64-byte-long-ecdsa-public-key-in-java
        private val P256_HEAD: ByteArray = Base64.getDecoder().decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE")
    }

    init {
        require(ecdsaData.size >= 128) { "Invalid length: ${ecdsaData.size}" }
    }

    fun getPublicKey(): PublicKey {
        val rawKey = ecdsaData.copyOfRange(64, 128)
        return generateP256PublicKeyFromFlatW(rawKey)
    }

    fun getSignature(): ByteArray {
        return getDerEncodedSignature(ecdsaData.copyOfRange(0, 64))
    }

    fun getQEReport(): ByteArray {
        val offset = 64 + 64 // skip isv signature and isv public key
        return ecdsaData.copyOfRange(offset, offset + 384)
    }

    fun getQESignature(): ByteArray {
        val offset = 64 + 64 + 384 // skip isv signature, isv public key, QE report
        return getDerEncodedSignature(ecdsaData.copyOfRange(offset, offset + 64))
    }

    fun getPublicKeyRaw(): ByteArray {
        return ecdsaData.copyOfRange(64, 128)
    }

    fun getAuthDataRaw(): ByteArray {
        var offset = 64 + 64 + 384 + 64
        val size = (ecdsaData[offset].toInt() and 0x00FF) + (ecdsaData[offset + 1].toInt() and 0x00FF) * 256
        offset += 2
        return ecdsaData.copyOfRange(offset, offset + size)
    }

    // convert raw 2x32 data to DER stream
    // converting 'raw' 64 bytes value to sequence of two 32 bytes values
    private fun getDerEncodedSignature(data64: ByteArray): ByteArray {
        val b1 = BigInteger(1, data64.copyOfRange(0, 32))
        val b2 = BigInteger(1, data64.copyOfRange(32, 64))

        val data1: ByteArray = b1.toByteArray()
        val data2: ByteArray = b2.toByteArray()

        val output = ByteArray(6 + data1.size + data2.size)
        output[0] = 0x30
        output[1] = (4 + data1.size + data2.size).toByte()
        encodeChunk(output, 2, data1)
        encodeChunk(output, 4 + data1.size, data2)
        return output
    }

    private fun encodeChunk(output: ByteArray, offset: Int, chunk: ByteArray) {
        output[offset] = 0x02
        output[offset + 1] = chunk.size.toByte()
        System.arraycopy(chunk, 0, output, offset + 2, chunk.size)
    }

    /**
     * Converts an uncompressed secp256r1 / P-256 public point to the EC public key it is representing.
     * @param w a 64 byte uncompressed EC point consisting of just a 256-bit X and Y
     * @return an `ECPublicKey` that the point represents
     */
    private fun generateP256PublicKeyFromFlatW(w: ByteArray): PublicKey {
        val encodedKey = ByteArray(P256_HEAD.size + w.size)
        System.arraycopy(P256_HEAD, 0, encodedKey, 0, P256_HEAD.size)
        System.arraycopy(w, 0, encodedKey, P256_HEAD.size, w.size)
        val ecpks = X509EncodedKeySpec(encodedKey)
        return KeyFactory.getInstance("EC").generatePublic(ecpks)
    }
}
