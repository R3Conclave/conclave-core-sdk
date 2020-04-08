package com.r3.conclave.common

import com.r3.conclave.common.internal.parseHex
import com.r3.conclave.common.internal.toHexString
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * A simple class that wraps a byte array and makes the equals/hashCode/toString methods work as you actually expect.
 * In an ideal JVM this would be a value type and be completely overhead free.
 */
open class OpaqueBytes(private val _bytes: ByteArray) {
    val size: Int get() = _bytes.size

    /** Returns a copy of the underlying byte array. */
    val bytes: ByteArray get() = _bytes.clone()

    /** Returns a [ByteArrayInputStream] of the bytes. */
    fun open(): ByteArrayInputStream = ByteArrayInputStream(_bytes)

    /** Returns a read-only [ByteBuffer] that is backed by the bytes. */
    fun buffer(): ByteBuffer = ByteBuffer.wrap(_bytes).asReadOnlyBuffer()

    /** Write the bytes to an [OutputStream]. */
    fun writeTo(output: OutputStream): Unit = output.write(_bytes)

    /** Write the bytes to a [ByteBuffer]. */
    fun putTo(buffer: ByteBuffer): ByteBuffer = buffer.put(_bytes)

    /**
     * Returns true iff this sequence of opaque bytes is equal to the sequence of [other], and that they are both of the
     * same class.
     *
     * This means subclasses of [OpaqueBytes] are never equal to each other or to [OpaqueBytes].
     */
    final override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is OpaqueBytes || this.javaClass != other.javaClass) return false
        return other._bytes.contentEquals(this._bytes)
    }

    override fun hashCode(): Int = _bytes.contentHashCode()

    override fun toString(): String = _bytes.toHexString()

    companion object {
        /**
         * Create [OpaqueBytes] from a sequence of [Byte] values.
         */
        @JvmStatic
        fun of(vararg b: Byte) = OpaqueBytes(b)

        /**
         * Parses the string of hexadecimal digits into an [OpaqueBytes].
         *
         * @throws IllegalArgumentException if the string contains incorrectly-encoded characters.
         */
        @JvmStatic
        fun parse(str: String): OpaqueBytes = OpaqueBytes(parseHex(str.toUpperCase()))
    }
}
