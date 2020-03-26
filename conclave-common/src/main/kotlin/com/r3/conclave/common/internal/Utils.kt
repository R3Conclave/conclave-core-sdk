package com.r3.conclave.common.internal

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

private val hexCode = "0123456789ABCDEF".toCharArray()

fun ByteArray.toHexString(): String {
    val builder = StringBuilder(size * 2)
    for (b in this) {
        builder.append(hexCode[(b.toInt() shr 4) and 0xF])
        builder.append(hexCode[b.toInt() and 0xF])
    }
    return builder.toString()
}

fun parseHex(str: String): ByteArray {
    val len = str.length

    // "111" is not a valid hex encoding.
    if (len % 2 != 0) {
        throw IllegalArgumentException("hex binary needs to be even-length: $str")
    }

    val out = ByteArray(len / 2)

    fun hexToBin(ch: Char): Int {
        return when (ch) {
            in '0'..'9' -> ch - '0'
            in 'A'..'F' -> ch - 'A' + 10
            else -> if (ch in 'a'..'f') ch - 'a' + 10 else -1
        }
    }

    var i = 0
    while (i < len) {
        val h = hexToBin(str[i])
        val l = hexToBin(str[i + 1])
        if (h == -1 || l == -1) {
            throw IllegalArgumentException("contains illegal character for hexBinary: $str")
        }

        out[i / 2] = (h * 16 + l).toByte()
        i += 2
    }

    return out
}

fun ByteBuffer.getBoolean(): Boolean = get().toInt() != 0

fun ByteBuffer.putBoolean(value: Boolean): ByteBuffer = put((if (value) 1 else 0).toByte())

fun ByteBuffer.getBytes(length: Int): ByteArray = ByteArray(length).also { get(it) }

fun ByteBuffer.getRemainingBytes(): ByteArray = getBytes(remaining())

fun DataInputStream.readBytes(length: Int): ByteArray {
    val bytes = ByteArray(length)
    readFully(bytes)
    return bytes
}

fun DataInputStream.readLengthPrefixBytes(): ByteArray = readBytes(readInt())

fun DataOutputStream.writeLengthPrefixBytes(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}
