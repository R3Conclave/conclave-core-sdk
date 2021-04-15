package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.*
import java.lang.reflect.Modifier
import java.nio.Buffer
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

sealed class Encoder<R> {
    /**
     * The minimum size in bytes the encoded value can have, where any variable length components are empty.
     */
    abstract val minSize: Int

    /**
     * Return the size of the encoded value that's at the current position of the given [buffer]. The position of the
     * buffer is not changed by this method.
     */
    abstract fun size(buffer: ByteBuffer): Int

    /**
     * Skip over the encoded value at the current position of the [buffer].
     *
     * The number of bytes skipped is equal to [size] but can also be determined by noting the [buffer]'s position before
     * and after this call.
     */
    abstract fun skip(buffer: ByteBuffer)

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

/**
 * Represents a fixed size encoded value.
 */
sealed class FixedEncoder<R> : Encoder<R>() {
    /**
     * The size of the encoded value in bytes.
     */
    abstract val size: Int

    final override val minSize: Int get() = size
    final override fun size(buffer: ByteBuffer): Int = size
    final override fun skip(buffer: ByteBuffer) {
        buffer.addPosition(size)
    }
}

/**
 * Represents a variable sized encoded value. [write] is not supported.
 */
sealed class VariableEncoder<R> : Encoder<R>() {
    final override fun write(buffer: ByteBuffer, value: R): ByteBuffer {
        throw UnsupportedOperationException("Cannot write to a variable type.")
    }
}

abstract class ByteBufferEncoder : FixedEncoder<ByteBuffer>() {
    final override fun read(buffer: ByteBuffer): ByteBuffer = buffer.getSlice(size).asReadOnlyBuffer()
    final override fun write(buffer: ByteBuffer, value: ByteBuffer): ByteBuffer {
        require(value.remaining() == size) {
            "Writing ${javaClass.simpleName}, expected $size bytes, got ${value.remaining()}"
        }
        return buffer.put(value)
    }
}

interface AbstractStruct {
    interface Field<in S : Encoder<*>, T : Encoder<*>> {
        val type: T
        fun align(buffer: ByteBuffer)
    }
}

/**
 * Represents a C-struct where all the fields are of fixed size.
 */
abstract class Struct : AbstractStruct, ByteBufferEncoder() {
    private var structSize = 0

    final override val size get() = structSize

    private inner class Field<in S : Struct, T : FixedEncoder<*>>(override val type: T) : AbstractStruct.Field<S, T> {
        private val offset = structSize

        init {
            structSize += type.size
        }

        override fun align(buffer: ByteBuffer) {
            buffer.addPosition(offset)
        }
    }

    protected fun <S : Struct, T : FixedEncoder<*>> S.field(type: T): AbstractStruct.Field<S, T> = Field(type)
}

/**
 * Represents a C-struct where some of the fields are of variable size.
 */
abstract class VariableStruct : AbstractStruct, VariableEncoder<ByteBuffer>() {
    private var startingFixedSize = 0
    private var fieldEncoders: MutableList<Encoder<*>>? = null
    private var structMinSize = 0

    private inner class Field<in S : VariableStruct, T : Encoder<*>>(override val type: T) :
        AbstractStruct.Field<S, T> {
        private val fixedOffset = startingFixedSize
        private val encodersIndex = fieldEncoders?.size ?: -1

        init {
            if (type is FixedEncoder<*> && fieldEncoders == null) {
                // Optimise for the scenerio where the first N fields are fixed size.
                startingFixedSize += type.size
            } else {
                val encoders = fieldEncoders ?: ArrayList<Encoder<*>>().also { fieldEncoders = it }
                encoders.add(type)
            }
            structMinSize += type.minSize
        }

        override fun align(buffer: ByteBuffer) {
            buffer.addPosition(fixedOffset)
            val encoders = fieldEncoders
            if (encoders != null) {
                for (i in 0 until encodersIndex) {
                    encoders[i].skip(buffer)
                }
            }
        }
    }

    final override val minSize: Int get() = structMinSize

    final override fun size(buffer: ByteBuffer): Int {
        val startPos = buffer.position()
        val endPos = try {
            skip(buffer)
            buffer.position()
        } finally {
            (buffer as Buffer).position(startPos)
        }
        return endPos - startPos
    }

    final override fun skip(buffer: ByteBuffer) {
        buffer.addPosition(startingFixedSize)
        fieldEncoders?.forEach { it.skip(buffer) }
    }

    final override fun read(buffer: ByteBuffer): ByteBuffer {
        val size = size(buffer)
        return buffer.getSlice(size).asReadOnlyBuffer()
    }

    protected fun <S : VariableStruct, T : Encoder<*>> S.field(type: T): AbstractStruct.Field<S, T> = Field(type)
}

abstract class VariableBytes : VariableEncoder<ByteBuffer>() {
    final override fun size(buffer: ByteBuffer): Int = minSize + getSizeAbsolute(buffer)
    final override fun skip(buffer: ByteBuffer) {
        val size = getSizeRelative(buffer)
        buffer.addPosition(size)
    }

    final override fun read(buffer: ByteBuffer): ByteBuffer {
        val size = getSizeRelative(buffer)
        return buffer.getSlice(size).asReadOnlyBuffer()
    }

    protected abstract fun getSizeRelative(buffer: ByteBuffer): Int
    protected abstract fun getSizeAbsolute(buffer: ByteBuffer): Int
}

open class UInt16VariableBytes : VariableBytes() {
    final override val minSize: Int get() = Short.SIZE_BYTES
    final override fun getSizeRelative(buffer: ByteBuffer): Int = buffer.getUnsignedShort()
    final override fun getSizeAbsolute(buffer: ByteBuffer): Int = buffer.getUnsignedShort(buffer.position())
}

open class UInt32VariableBytes : VariableBytes() {
    final override val minSize: Int get() = Int.SIZE_BYTES
    final override fun getSizeRelative(buffer: ByteBuffer): Int = buffer.getSize { getInt() }
    final override fun getSizeAbsolute(buffer: ByteBuffer): Int = buffer.getSize { getInt(position()) }
    private inline fun ByteBuffer.getSize(block: ByteBuffer.() -> Int): Int {
        val size = block(this)
        check(size >= 0) { "ByteBuffer does not support sizes greater than max signed int." }
        return size
    }
}

open class UInt16 : FixedEncoder<Int>() {
    final override val size get() = Short.SIZE_BYTES
    final override fun read(buffer: ByteBuffer): Int = buffer.getUnsignedShort()
    final override fun write(buffer: ByteBuffer, value: Int): ByteBuffer = buffer.putUnsignedShort(value)
}

open class Int32 : FixedEncoder<Int>() {
    final override val size get() = Int.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getInt()
    final override fun write(buffer: ByteBuffer, value: Int): ByteBuffer = buffer.putInt(value)
}

open class UInt32 : FixedEncoder<Long>() {
    final override val size get() = Int.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getUnsignedInt()
    final override fun write(buffer: ByteBuffer, value: Long): ByteBuffer {
        require(value >= 0 && value <= 4294967295) { "Not an unsigned int: $value" }
        return buffer.putInt(value.toInt())
    }
}

open class Int64 : FixedEncoder<Long>() {
    final override val size get() = Long.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.long
    final override fun write(buffer: ByteBuffer, value: Long): ByteBuffer = buffer.putLong(value)
}

open class FixedBytes(final override val size: Int) : ByteBufferEncoder() {
    init {
        require(size >= 0) { size }
    }
}

class ReservedBytes(override val size: Int) : FixedEncoder<ByteBuffer>() {
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

private inline fun <reified R> values(encoder: FixedEncoder<R>): Map<String, R> {
    val values = LinkedHashMap<String, R>()
    for (field in encoder.javaClass.fields) {
        if (!Modifier.isStatic(field.modifiers)) continue
        val value = field.get(null) as? R ?: continue
        values[field.name] = value
    }
    return Collections.unmodifiableMap(values)
}

fun Cursor<Flags64, Long>.isSet(flag: Long): Boolean = read() and flag != 0L
fun Cursor<Flags16, Int>.isSet(flag: Int): Boolean = read() and flag != 0
