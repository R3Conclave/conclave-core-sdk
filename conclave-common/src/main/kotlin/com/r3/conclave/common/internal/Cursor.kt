package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.getSlice
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
// TODO Endianness should be a property of the Encoder, not the Cursor
class Cursor<out T : Encoder<R>, R> private constructor(val encoder: T, private val underlyingBuffer: ByteBuffer) {
    companion object {
        /**
         * Allocates a new cursor for an empty fixed size value [T].
         *
         * The new [Cursor] will be backed by a byte array which can be accessed by calling `buffer.array()`.
         */
        fun <T : FixedEncoder<R>, R> allocate(type: T): Cursor<T, R> = Cursor(type, ByteBuffer.allocate(type.size))

        /**
         * Wraps a byte array into a cursor for [T]. [length] must be exactly the size of [type] as encoded by the bytes.
         *
         * The new [Cursor] will be backed by the given byte array; that is, modifications made via the cursor will cause
         * the array to be modified and vice versa. Access to the array is possible by calling `buffer.array()`.
         *
         * @throws IllegalArgumentException If [length] is not the size of [type].
         */
        fun <T : Encoder<R>, R> wrap(type: T, bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Cursor<T, R> {
            val buffer = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.LITTLE_ENDIAN)
            val size = type.size(buffer)
            require(length == size) { "Size of ${type.javaClass.simpleName} is $size whereas the length provided is $length." }
            return Cursor(type, buffer)
        }

        /**
         * Returns a cursor over the remaining bytes of [buffer] to capture [type]. The position of the buffer is
         * advanced by the size of [type].
         *
         * @throws IllegalArgumentException If the remaining bytes of [buffer] is less than the size of [type].
         */
        fun <T : Encoder<R>, R> read(type: T, buffer: ByteBuffer): Cursor<T, R> {
            val size = type.size(buffer.order(ByteOrder.LITTLE_ENDIAN))
            require(buffer.remaining() >= size) {
                "Size of ${type.javaClass.simpleName} is $size whereas there are only ${buffer.remaining()} bytes remaining in the buffer."
            }
            return Cursor(type, buffer.getSlice(size))
        }
    }

    /** Get the [ByteBuffer] of the pointed-to data. */
    val buffer: ByteBuffer get() = underlyingBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    /** Get the encoded bytes of [R]. */
    val bytes: ByteArray get() = buffer.getRemainingBytes()

    val size: Int get() = underlyingBuffer.remaining()

    /** Read the pointed-to data into [R]. */
    fun read(): R = encoder.read(buffer)

    /** Write the pointed-to data from [R]. */
    fun write(value: R): ByteBuffer = encoder.write(buffer, value)

    override fun toString(): String = CursorPrettyPrint.print(this)

    override fun equals(other: Any?): Boolean {
        return this === other || other is Cursor<*, *> && this.encoder == other.encoder && this.read() == other.read()
    }

    override fun hashCode(): Int = 31 * encoder.hashCode() + read().hashCode()

    /**
     * Allows field modifications like so:
     *
     * ```
     * val report = Cursor.wrap(SgxReport, reportBytes)
     * report[SgxReport.body][SgxReportBody.mrenclave] = measurement
     * ```
     */
    operator fun <FR, FT : FixedEncoder<FR>> set(field: AbstractStruct.Field<T, FT>, value: FR) {
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
    operator fun <FT : Encoder<FR>, FR> get(field: AbstractStruct.Field<T, FT>): Cursor<FT, FR> {
        val buffer = this.buffer
        field.align(buffer)
        (buffer as Buffer).limit(buffer.position() + field.type.size(buffer))
        return Cursor(field.type, buffer)
    }
}
