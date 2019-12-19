package com.r3.conclave.common

import com.r3.conclave.common.internal.getBytes
import com.r3.conclave.common.internal.parseHex
import com.r3.conclave.common.internal.toHexString
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Container for a cryptographically secure hash value. Currently only SHA-256 and SHA-512 are supported, represented
 * by [SHA256Hash] and [SHA512Hash] respectively.
 */
sealed class SecureHash(private val _bytes: ByteArray) {
    // TODO Doesn't make sense to have size when it's already known. Add this to an OpaqueBytes class if we decide to have that.
//    val size: Int get() = _bytes.size

    /** Returns a copy of the underlying byte array. */
    val bytes: ByteArray get() = _bytes.clone()

    /** Returns a [ByteArrayInputStream] of the bytes. */
    fun open(): ByteArrayInputStream = ByteArrayInputStream(_bytes)

    /** Write the bytes to an [OutputStream]. */
    fun writeTo(output: OutputStream): Unit = output.write(_bytes)

    /** Write the bytes to a [ByteBuffer]. */
    fun putTo(buffer: ByteBuffer): ByteBuffer = buffer.put(_bytes)

    override fun equals(other: Any?): Boolean {
        return other === this || other is SecureHash && other._bytes.contentEquals(this._bytes)
    }

    override fun hashCode(): Int = _bytes.contentHashCode()

    override fun toString(): String = _bytes.toHexString()
}

/** SHA-256 is part of the SHA-2 hash function family. Generated hash is fixed size, 256-bits (32-bytes). */
class SHA256Hash private constructor(bytes: ByteArray) : SecureHash(bytes) {
    init {
        require(bytes.size == 32) { "Invalid hash size, must be 32 bytes" }
    }

    companion object {
        /**
         * Computes the SHA-256 hash value of the [ByteArray].
         * @param bytes The [ByteArray] to hash.
         */
        @JvmStatic
        fun hash(bytes: ByteArray): SHA256Hash = SHA256Hash(MessageDigest.getInstance("SHA-256").digest(bytes))

        /**
         * Wraps the given byte array into a [SHA256Hash] object. The number of bytes must be 32.
         *
         * Note: The bytes are NOT hashed.
         */
        @JvmStatic
        fun wrap(bytes: ByteArray): SHA256Hash = SHA256Hash(bytes.clone())

        /**
         * Returns the next 32 bytes of the buffer as a [SHA256Hash]. The position of the buffer increased by 32.
         *
         * @throws java.nio.BufferUnderflowException If fewer than 32 bytes are remaining in the buffer.
         */
        @JvmStatic
        fun get(buffer: ByteBuffer): SHA256Hash = SHA256Hash(buffer.getBytes(32))

        /**
         * Converts a SHA-256 hash value represented as a hexadecimal [String] into a [SecureHash].
         * @param str A sequence of 64 hexadecimal digits that represents a SHA-256 hash value.
         * @throws IllegalArgumentException The input string does not contain 64 hexadecimal digits, or it contains incorrectly-encoded characters.
         */
        @JvmStatic
        fun parse(str: String): SHA256Hash {
            return parseHex(str.toUpperCase()).let {
                when (it.size) {
                    32 -> SHA256Hash(it)
                    else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 32 bytes in hex: $str")
                }
            }
        }
    }
}

/** SHA-512 is part of the SHA-2 hash function family. Generated hash is fixed size, 512-bits (64-bytes). */
class SHA512Hash private constructor(bytes: ByteArray) : SecureHash(bytes) {
    init {
        require(bytes.size == 64) { "Invalid hash size, must be 64 bytes" }
    }

    companion object {
        /**
         * Computes the SHA-256 hash value of the [ByteArray].
         * @param bytes The [ByteArray] to hash.
         */
        @JvmStatic
        fun hash(bytes: ByteArray): SHA512Hash = SHA512Hash(MessageDigest.getInstance("SHA-512").digest(bytes))

        /**
         * Wraps the given byte array into a [SHA512Hash] object. The number of bytes must be 64.
         *
         * Note: The bytes are NOT hashed.
         */
        @JvmStatic
        fun wrap(bytes: ByteArray): SHA512Hash = SHA512Hash(bytes.clone())

        /**
         * Returns the next 64 bytes of the buffer as a [SHA512Hash]. The position of the buffer increased by 64.
         *
         * @throws java.nio.BufferUnderflowException If fewer than 64 bytes are remaining in the buffer.
         */
        @JvmStatic
        fun get(buffer: ByteBuffer): SHA512Hash = SHA512Hash(buffer.getBytes(64))

        /**
         * Converts a SHA-256 hash value represented as a hexadecimal [String] into a [SecureHash].
         * @param str A sequence of 64 hexadecimal digits that represents a SHA-256 hash value.
         * @throws IllegalArgumentException The input string does not contain 64 hexadecimal digits, or it contains incorrectly-encoded characters.
         */
        @JvmStatic
        fun parse(str: String): SHA512Hash {
            return parseHex(str.toUpperCase()).let {
                when (it.size) {
                    64 -> SHA512Hash(it)
                    else -> throw IllegalArgumentException("Provided string is ${it.size} bytes not 64 bytes in hex: $str")
                }
            }
        }
    }
}
