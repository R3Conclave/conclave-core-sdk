package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.addPosition
import com.r3.conclave.utilities.internal.getRemainingBytes
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
class Cursor<out T : Encoder<R>, R> private constructor(val encoder: T, private val underlyingBuffer: ByteBuffer) {
    companion object {
        /**
         * Allocates a new cursor for an empty [T].
         *
         * The new [Cursor] will be backed by a byte array which can be accessed by calling `buffer.array()`.
         */
        fun <T : Encoder<R>, R> allocate(type: T): Cursor<T, R> {
            return Cursor(type, ByteBuffer.allocate(type.size))
        }

        /**
         * Wraps a byte array into a cursor for [T]. [length] must be exactly the size of [type].
         *
         * The new [Cursor] will be backed by the given byte array; that is, modifications made via the cursor will cause
         * the array to be modified and vice versa. Access to the array is possible by calling `buffer.array()`.
         *
         * @throws IllegalArgumentException If [length] is not the size of [type].
         */
        fun <T : Encoder<R>, R> wrap(type: T, bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Cursor<T, R> {
            return Cursor(type, ByteBuffer.wrap(bytes, offset, length))
        }

        /**
         * Returns a cursor over the remaining bytes of [buffer] to capture [type]. The position of the buffer is
         * advanced by the size of [type].
         *
         * @throws IllegalArgumentException If the remaining bytes of [buffer] is less than the size of [type].
         */
        fun <T : Encoder<R>, R> read(type: T, buffer: ByteBuffer): Cursor<T, R> {
            require(buffer.remaining() >= type.size) {
                "There are insufficient remaining bytes for ${type.javaClass.simpleName}. " +
                        "Remaining=${buffer.remaining()}, required=${type.size}"
            }
            val result = buffer.slice()
            (result as Buffer).limit(type.size)
            buffer.addPosition(type.size)
            return Cursor(type, result)
        }
    }

    init {
        require(underlyingBuffer.remaining() == encoder.size) {
            "Passed in buffer's remaining() = ${buffer.remaining()} whereas ${encoder.javaClass.simpleName} requires ${encoder.size}"
        }
    }

    /** Get the [ByteBuffer] of the pointed-to data. */
    val buffer: ByteBuffer get() = underlyingBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    /** Get the encoded bytes of [R]. */
    val bytes: ByteArray get() = buffer.getRemainingBytes()

    /** Read the pointed-to data into [R]. */
    fun read(): R = encoder.read(buffer)

    /** Write the pointed-to data from [R]. */
    fun write(value: R): ByteBuffer = encoder.write(buffer, value)

    override fun toString(): String = CursorPrettyPrint.print(this)

    override fun equals(other: Any?): Boolean = this === other || other is Cursor<*, *> && other.read() == this.read()

    override fun hashCode(): Int = read().hashCode()

    /**
     * Allows field modifications like so:
     *
     * ```
     * val report = Cursor.wrap(SgxReport, reportBytes)
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
     * val report = Cursor.wrap(SgxReport, reportBytes)
     * val measurement = report[SgxReport.body][SgxReportBody.mrenclave]
     * ```
     */
    operator fun <FT : Encoder<FR>, FR> get(field: Struct.Field<T, FT>): Cursor<FT, FR> {
        val buffer = this.buffer
        buffer.addPosition(field.offset)
        (buffer as Buffer).limit(buffer.position() + field.type.size)
        return Cursor(field.type, buffer)
    }

}

operator fun <ET : Encoder<ER>, ER> Cursor<CArray<ER, ET>, List<ER>>.get(index: Int): Cursor<ET, ER> {
    val buffer = this.buffer
    buffer.addPosition(index * encoder.elementType.size)
    (buffer as Buffer).limit(buffer.position() + encoder.elementType.size)
    return Cursor.read(encoder.elementType, buffer)
}

operator fun <ET : Encoder<ER>, ER> Cursor<CArray<ER, ET>, List<ER>>.set(index: Int, value: ER) {
    get(index).write(value)
}
