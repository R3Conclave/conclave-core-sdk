package com.r3.conclave.common

import com.r3.conclave.utilities.internal.getBytes
import com.r3.conclave.utilities.internal.parseHex
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Container for a cryptographically secure hash value. Currently only SHA-256 and SHA-512 are supported, represented
 * by [SHA256Hash] and [SHA512Hash] respectively.
 */
sealed class SecureHash(bytes: ByteArray) : OpaqueBytes(bytes) {
    /**
     * Suppress kotlin specific companion objects from our API documentation.
     * The public items within the object are still published in the documentation.
     * @suppress
     */
    companion object {
        /**
         * Parses the given hexadecimal string into either a [SHA256Hash] or a [SHA512Hash] depending on the length.
         */
        @JvmStatic
        fun parse(str: String): SecureHash {
            return when (str.length) {
                64 -> SHA256Hash.parse(str)
                128 -> SHA512Hash.parse(str)
                else -> throw IllegalArgumentException("Provided string is neither a SHA-256 or a SHA-512 string: $str")
            }
        }
    }
}

/** SHA-256 is part of the SHA-2 hash function family. Generated hash is fixed size, 256-bits (32-bytes). */
class SHA256Hash private constructor(bytes: ByteArray) : SecureHash(bytes) {
    init {
        require(bytes.size == 32) { "Invalid hash size, must be 32 bytes" }
    }

    /**
     * Suppress kotlin specific companion objects from our API documentation.
     * The public items within the object are still published in the documentation.
     * @suppress
     */
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
         * Gets the next 32 bytes of the buffer and returns it as a [SHA256Hash]. The position of the buffer increased by 32.
         *
         * @throws BufferUnderflowException If fewer than 32 bytes are remaining in the buffer.
         */
        @JvmStatic
        fun get(buffer: ByteBuffer): SHA256Hash = SHA256Hash(buffer.getBytes(32))

        /**
         * Converts a SHA-256 hash value represented as a hexadecimal [String] into a [SHA256Hash].
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

    /**
     * Suppress kotlin specific companion objects from our API documentation.
     * The public items within the object are still published in the documentation.
     * @suppress
     */
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
         * Gets the the next 64 bytes of the buffer and returns it as a [SHA512Hash]. The position of the buffer increased by 64.
         *
         * @throws BufferUnderflowException If fewer than 64 bytes are remaining in the buffer.
         */
        @JvmStatic
        fun get(buffer: ByteBuffer): SHA512Hash = SHA512Hash(buffer.getBytes(64))

        /**
         * Converts a SHA-256 hash value represented as a hexadecimal [String] into a [SHA512Hash].
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
