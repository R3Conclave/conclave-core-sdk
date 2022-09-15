package com.r3.conclave.common.internal

import java.nio.ByteBuffer

interface CallHandler {
    fun handleCall(messageBuffer: ByteBuffer?): ByteBuffer?
}

/**
 * Encodes a "Long" as a sequence of bytes such that smaller values require fewer bytes to encode.
 * The first seven bits of each byte encode bits in the Long value.
 * If the MSB of a byte is one, the sequence continues.
 * If the MSB of a byte is zero, the sequence ends.
 */
fun ByteBuffer.encodeDynamicLengthField(length: Long) {
    var tmp: Long = length
    do {
        var byte = tmp and 0x7f
        tmp = tmp shr 7
        if (tmp != 0L) {
            byte = byte or 0x10
        }
        this.put(byte.toByte())
    } while (tmp != 0L)
}

/**
 * Corresponding decode operation.
 */
fun ByteBuffer.decodeDynamicLengthField(): Long {
    var length: Long = 0
    var byte: Int
    var fieldOffset = 0
    do {
        byte = this.get().toInt()
        length = length or ((byte and 0x7f).toLong() shl fieldOffset)
        fieldOffset += 7
    } while (byte and 0x10 != 0)
    return length
}
