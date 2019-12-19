package com.r3.sgx.core.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * This class implements a cursor into a [ByteBuffer]. It's purpose is to provide a C-like typed view over serialized
 * data using [Encoder]s.
 *
 * @param R the pointed-to data's Kotlin representation type.
 * @param T the [Encoder] describing how to read/write the pointed-to data.
 */
class Cursor<R, out T : Encoder<R>>(val encoder: T, buffer: ByteBuffer) {
    companion object {
        @JvmStatic
        fun <R, T : Encoder<R>> allocate(type: T): Cursor<R, T> {
            return Cursor(type, ByteBuffer.allocate(type.size()).order(ByteOrder.LITTLE_ENDIAN))
        }

        fun <R, T : Encoder<R>> wrap(type: T, bytes: ByteArray): Cursor<R, T> = Cursor(type, ByteBuffer.wrap(bytes))
    }

    init {
        require(buffer.remaining() == encoder.size()) {
            "Passed in buffer's remaining() = ${buffer.remaining()} whereas ${encoder.javaClass.simpleName} requires ${encoder.size()}"
        }
    }

    private val originalBuffer = buffer.duplicate()

    /** Get the [ByteBuffer] of the pointed-to data. */
    fun getBuffer(): ByteBuffer = originalBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    /** Read the pointed-to data into [R] */
    fun read(): R = encoder.read(getBuffer())
    /** Write the pointed-to data from [R] */
    fun write(value: R) = encoder.write(getBuffer(), value)

    override fun toString(): String = CursorPrettyPrint.print(this)

    override fun equals(other: Any?): Boolean {
        if (other !is Cursor<*, *>) return false
        if (encoder != other.encoder) return false
        return read() == other.read()
    }

    override fun hashCode(): Int {
        return originalBuffer.hashCode()
    }

    /**
     * Allows field modifications like so:
     *
     * val report = Cursor([SgxReport], reportBytes)
     * report[[SgxReport.body]][[SgxReportBody.measurement]] = measurement
     */
    operator fun <FR, FT : Encoder<FR>> set(field: Struct.Field<T, FT>, value: FR) {
        get(field).write(value)
    }

    /**
     * Allows field accesses like so:
     *
     * val report = Cursor([SgxReport], reportBytes)
     * val measurement = report[[SgxReport.body]][[SgxReportBody.measurement]]
     */
    operator fun <FR, FT : Encoder<FR>> get(field: Struct.Field<T, FT>): Cursor<FR, FT> {
        val buffer = getBuffer()
        buffer.position(buffer.position() + field.offset)
        buffer.limit(buffer.position() + field.type.size())
        return Cursor(field.type, buffer)
    }

}

operator fun <ER, ET : Encoder<ER>> Cursor<List<ER>, CArray<ER, ET>>.get(index: Int): Cursor<ER, ET> {
    val buffer = getBuffer()
    buffer.position(buffer.position() + index * encoder.elementType.size())
    buffer.limit(buffer.position() + encoder.elementType.size())
    return Cursor(encoder.elementType, buffer)
}

operator fun <ER, ET : Encoder<ER>> Cursor<List<ER>, CArray<ER, ET>>.set(index: Int, value: ER) {
    get(index).write(value)
}
