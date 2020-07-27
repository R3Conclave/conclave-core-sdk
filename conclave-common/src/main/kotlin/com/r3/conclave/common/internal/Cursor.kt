package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.getBytes
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

typealias ByteCursor<T> = Cursor<T, ByteBuffer>

/**
 * This class implements a cursor into a [ByteBuffer]. It's purpose is to provide a C-like typed view over serialized
 * data using [Encoder]s.
 *
 * @param T the [Encoder] describing how to read/write the pointed-to data.
 * @param R the pointed-to data's Kotlin representation type.
 */
class Cursor<out T : Encoder<R>, R>(val encoder: T, buffer: ByteBuffer) {
    constructor(encoder: T, bytes: ByteArray) : this(encoder, ByteBuffer.wrap(bytes))

    companion object {
        @JvmStatic
        fun <T : Encoder<R>, R> allocate(type: T): Cursor<T, R> {
            return Cursor(type, ByteBuffer.allocate(type.size()).order(ByteOrder.LITTLE_ENDIAN))
        }
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

    fun readBytes(): ByteArray = getBuffer().getBytes(encoder.size())

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
     * ```
     * val report = Cursor(SgxReport, reportBytes)
     * report[SgxReport.body][SgxReportBody.mrenclave] = measurement
     * ```
     */
    operator fun <FR, FT : Encoder<FR>> set(field: Struct.Field<T, FT>, value: FR) {
        get(field).write(value)
    }

    /**
     * Allows field accesses like so:
     *
     * ```
     * val report = Cursor(SgxReport, reportBytes)
     * val measurement = report[SgxReport.body][SgxReportBody.measurement]
     * ```
     */
    operator fun <FT : Encoder<FR>, FR> get(field: Struct.Field<T, FT>): Cursor<FT, FR> {
        val buffer = getBuffer()
        (buffer as Buffer).position(buffer.position() + field.offset)
        (buffer as Buffer).limit(buffer.position() + field.type.size())
        return Cursor(field.type, buffer)
    }

}

operator fun <ET : Encoder<ER>, ER> Cursor<CArray<ER, ET>, List<ER>>.get(index: Int): Cursor<ET, ER> {
    val buffer = getBuffer()
    buffer.position(buffer.position() + index * encoder.elementType.size())
    buffer.limit(buffer.position() + encoder.elementType.size())
    return Cursor(encoder.elementType, buffer)
}

operator fun <ET : Encoder<ER>, ER> Cursor<CArray<ER, ET>, List<ER>>.set(index: Int, value: ER) {
    get(index).write(value)
}
