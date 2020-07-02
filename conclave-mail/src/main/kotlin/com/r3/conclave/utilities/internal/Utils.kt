package com.r3.conclave.utilities.internal

import java.io.*
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

inline fun writeData(block: DataOutputStream.() -> Unit): ByteArray {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)
    block(dos)
    return baos.toByteArray()
}

fun ByteArray.dataStream(): DataInputStream = DataInputStream(inputStream())

inline fun <T> ByteArray.deserialise(block: DataInputStream.() -> T): T = block(dataStream())

/** Read and return exactly [n] bytes from the stream, or throw [EOFException]. */
fun DataInputStream.readNBytes(n: Int): ByteArray {
    val bytes = ByteArray(n)
    readFully(bytes)
    return bytes
}

fun DataInputStream.readIntLengthPrefixBytes(): ByteArray = readNBytes(readInt())

fun DataOutputStream.writeIntLengthPrefixBytes(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}

inline fun <T> DataOutputStream.nullableWrite(value: T?, block: DataOutputStream.(T) -> Unit) {
    writeBoolean(value == null)
    if (value != null) {
        block(this, value)
    }
}

inline fun <T> DataInputStream.nullableRead(block: DataInputStream.() -> T): T? {
    val isNull = readBoolean()
    return if (!isNull) block(this) else null
}

inline fun <T> DataOutputStream.writeList(collection: Collection<T>, block: DataOutputStream.(T) -> Unit) {
    writeInt(collection.size)
    for (element in collection) {
        block(this, element)
    }
}

inline fun <reified T> DataInputStream.readList(block: DataInputStream.() -> T): Array<T> {
    return Array(readInt()) { block(this) }
}

/** Reads this stream completely into a byte array and then closes it. */
fun InputStream.readFully(): ByteArray = use { it.readBytes() }
