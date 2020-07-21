package com.r3.conclave.common.internal

import java.nio.Buffer
import java.nio.ByteBuffer

sealed class Encoder<R> {
    abstract fun size(): Int
    abstract fun read(buffer: ByteBuffer): R
    abstract fun write(buffer: ByteBuffer, value: R): ByteBuffer
}

abstract class Struct : Encoder<ByteBuffer>() {
    private var structOffset = 0

    final override fun size() = structOffset
    final override fun read(buffer: ByteBuffer): ByteBuffer {
        val result = buffer.slice()
        result.limit(size())
        return result.asReadOnlyBuffer()
    }

    final override fun write(buffer: ByteBuffer, value: ByteBuffer): ByteBuffer {
        buffer.mark()
        buffer.put(value)
        buffer.reset()
        return buffer
    }

    inner class Field<in S, T : Encoder<*>>(val type: T) {
        val offset = structOffset
        init {
            structOffset += type.size()
        }
    }

    protected fun <S : Struct, T : Encoder<*>> S.field(type: T) = Field<S, T>(type)
}

open class Int16 : Encoder<Short>() {
    final override fun size() = Short.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getShort()
    final override fun write(buffer: ByteBuffer, value: Short): ByteBuffer = buffer.putShort(value)
}

open class UInt16 : Encoder<Int>() {
    final override fun size() = 2
    final override fun read(buffer: ByteBuffer) = buffer.getShort().toInt() and 0xFFFF
    final override fun write(buffer: ByteBuffer, value: Int): ByteBuffer {
        require(value >= 0 && value <= 65535) { "Not an unsigned short: $value" }
        return buffer.putShort((value and 0xFFFF).toShort())
    }
}

open class Int32 : Encoder<Int>() {
    final override fun size() = Int.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getInt()
    final override fun write(buffer: ByteBuffer, value: Int): ByteBuffer = buffer.putInt(value)
}

open class Int64 : Encoder<Long>() {
    final override fun size() = Long.SIZE_BYTES
    final override fun read(buffer: ByteBuffer) = buffer.getLong()
    final override fun write(buffer: ByteBuffer, value: Long): ByteBuffer = buffer.putLong(value)
}

open class FixedBytes(val size: Int) : Encoder<ByteBuffer>() {
    init {
        require(size >= 0) { size }
    }
    final override fun size() = size
    final override fun read(buffer: ByteBuffer): ByteBuffer {
        val result = buffer.slice()
        (result as Buffer).limit(size())
        return result.asReadOnlyBuffer()
    }
    final override fun write(buffer: ByteBuffer, value: ByteBuffer): ByteBuffer {
        require(value.remaining() == size) {
            "Writing ${FixedBytes::class.java.simpleName}, expected $size bytes, got ${value.remaining()}"
        }
        return buffer.put(value)
    }
}

class ReservedBytes(val size: Int) : Encoder<ByteBuffer>() {
    override fun size() = size
    override fun read(buffer: ByteBuffer): ByteBuffer {
        val result = buffer.slice()
        result.limit(size())
        return result.asReadOnlyBuffer()
    }
    override fun write(buffer: ByteBuffer, value: ByteBuffer): ByteBuffer {
        throw UnsupportedOperationException("Cannot write to reserved area")
    }
}

abstract class Enum32 : Int32()
abstract class Enum16 : Int16()

abstract class Flags64 : Int64()

class CArray<R, T : Encoder<R>>(val elementType: T, val length: Int): Encoder<List<R>>() {
    override fun size() = elementType.size() * length

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
