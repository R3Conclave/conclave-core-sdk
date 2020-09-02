package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.getSlice
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

sealed class Encoder<R> {
    /**
     * The size of the encoded value in bytes.
     */
    abstract val size: Int

    /**
     * Read from the given [ByteBuffer] from its current position and return the encoded value. The buffer's position
     * will be advanced by [size] bytes.
     */
    abstract fun read(buffer: ByteBuffer): R

    /**
     * Write the given encoded [value] into the given [ByteBuffer] at its current position. The buffer's position will
     * be advanced by [size] bytes.
     *
     * @return [buffer]
     */
    abstract fun write(buffer: ByteBuffer, value: R): ByteBuffer
}

abstract class ByteBufferEncoder : Encoder<ByteBuffer>() {
    final override fun read(buffer: ByteBuffer): ByteBuffer = buffer.getSlice(size).asReadOnlyBuffer()
    final override fun write(buffer: ByteBuffer, value: ByteBuffer): ByteBuffer {
        require(value.remaining() == size) {
            "Writing ${javaClass.simpleName}, expected $size bytes, got ${value.remaining()}"
        }
        return buffer.put(value)
    }
}

abstract class Struct : ByteBufferEncoder() {
    private var structOffset = 0

    final override val size get() = structOffset

    inner class Field<in S, T : Encoder<*>>(val type: T) {
        val offset = structOffset
        init {
            structOffset += type.size
        }
    }

    protected fun <S : Struct, T : Encoder<*>> S.field(type: T) = Field<S, T>(type)
}

open class Int16 : Encoder<Short>() {
    final override val size get() = Short.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getShort()
    final override fun write(buffer: ByteBuffer, value: Short): ByteBuffer = buffer.putShort(value)
}

open class UInt16 : Encoder<Int>() {
    final override val size get() = Short.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = java.lang.Short.toUnsignedInt(buffer.getShort())
    final override fun write(buffer: ByteBuffer, value: Int): ByteBuffer {
        require(value >= 0 && value <= 65535) { "Not an unsigned short: $value" }
        return buffer.putShort(value.toShort())
    }
}

open class Int32 : Encoder<Int>() {
    final override val size get() = Int.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getInt()
    final override fun write(buffer: ByteBuffer, value: Int): ByteBuffer = buffer.putInt(value)
}

open class UInt32 : Encoder<Long>() {
    final override val size get() = Int.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = Integer.toUnsignedLong(buffer.getInt())
    final override fun write(buffer: ByteBuffer, value: Long): ByteBuffer {
        require(value >= 0 && value <= 4294967295) { "Not an unsigned int: $value" }
        return buffer.putInt(value.toInt())
    }
}

open class Int64 : Encoder<Long>() {
    final override val size get() = Long.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getLong()
    final override fun write(buffer: ByteBuffer, value: Long): ByteBuffer = buffer.putLong(value)
}

open class FixedBytes(final override val size: Int) : ByteBufferEncoder() {
    init {
        require(size >= 0) { size }
    }
}

class ReservedBytes(override val size: Int) : Encoder<ByteBuffer>() {
    init {
        require(size >= 0) { size }
    }
    override fun read(buffer: ByteBuffer): ByteBuffer = buffer.getSlice(size).asReadOnlyBuffer()
    override fun write(buffer: ByteBuffer, value: ByteBuffer): ByteBuffer {
        throw UnsupportedOperationException("Cannot write to reserved area")
    }
}

interface EnumEncoder<R> {
    val values: Map<String, R>
}

abstract class Enum32 : Int32(), EnumEncoder<Int> {
    final override val values: Map<String, Int> by lazy { values(this) }
}
abstract class Enum16 : UInt16(), EnumEncoder<Int> {
    final override val values: Map<String, Int> by lazy { values(this) }
}

abstract class Flags64 : Int64() {
    val values: Map<String, Long> by lazy { values(this) }
}

abstract class Flags16 : UInt16() {
    val values: Map<String, Int> by lazy { values(this) }
}

private inline fun <reified R> values(encoder: Encoder<R>): Map<String, R> {
    val values = LinkedHashMap<String, R>()
    for (field in encoder.javaClass.fields) {
        if (!Modifier.isStatic(field.modifiers)) continue
        val value = field.get(null) as? R ?: continue
        values[field.name] = value
    }
    return Collections.unmodifiableMap(values)
}

class CArray<R, T : Encoder<R>>(val elementType: T, val length: Int) : Encoder<List<R>>() {
    override val size get() = elementType.size * length

    override fun read(buffer: ByteBuffer): List<R> {
        val result = ArrayList<R>(length)
        for (i in 1 .. length) {
            result.add(elementType.read(buffer))
        }
        return result
    }

    override fun write(buffer: ByteBuffer, value: List<R>): ByteBuffer {
        for (element in value) {
            elementType.write(buffer, element)
        }
        return buffer
    }
}

fun Cursor<Flags64, Long>.isSet(flag: Long): Boolean = read() and flag != 0L
fun Cursor<Flags16, Int>.isSet(flag: Int): Boolean = read() and flag != 0
