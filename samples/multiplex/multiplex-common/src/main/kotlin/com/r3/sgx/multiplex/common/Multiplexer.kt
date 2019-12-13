@file:JvmName("Multiplexer")
package com.r3.sgx.multiplex.common

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.MessageDigest

const val SHA256_BYTES = 32

val ByteBuffer.sha256: ByteArray get() {
    return MessageDigest.getInstance("SHA-256").let { hash ->
        hash.update(slice())
        hash.digest()
    }
}

val ByteArray.hashKey: String get() {
    // Our 256 bit hash consists of 64 nybbles.
    return BigInteger(1, this)
        .toString(16).toLowerCase().padStart(2 * SHA256_BYTES, '0')
}
