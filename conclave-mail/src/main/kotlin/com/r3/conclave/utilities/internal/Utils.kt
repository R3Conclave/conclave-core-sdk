package com.r3.conclave.utilities.internal

import java.io.*
import java.nio.Buffer
import java.nio.ByteBuffer
import java.security.MessageDigest

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
    require(len % 2 == 0) { "hex binary needs to be even-length: $str" }

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
        require(h != -1 && l != -1) { "contains illegal character for hexBinary: $str" }
        out[i / 2] = (h * 16 + l).toByte()
        i += 2
    }

    return out
}

inline fun digest(algorithm: String, block: MessageDigest.() -> Unit): ByteArray {
    val messageDigest = MessageDigest.getInstance(algorithm)
    block(messageDigest)
    return messageDigest.digest()
}

fun ByteBuffer.getBoolean(): Boolean = get().toInt() != 0

fun ByteBuffer.putBoolean(value: Boolean): ByteBuffer = put((if (value) 1 else 0).toByte())

fun ByteBuffer.getBytes(length: Int): ByteArray = ByteArray(length).also { get(it) }

/**
 * Read the remaining bytes of the buffer. The buffer is exhausted after this operation, the position equal to the
 * limit and [ByteBuffer.hasRemaining] `false`.
 *
 * @param avoidCopying If `true` then an attempt is made to avoid copying from the buffer and the underlying byte array
 * is used directly if it's available. Because of this only specify `true` if the byte array will not be written to.
 * The default is `false`, i.e. the returned array is a copy and it's safe to modify it.
 */
fun ByteBuffer.getRemainingBytes(avoidCopying: Boolean = false): ByteArray {
    if (avoidCopying && hasArray()) {
        val byteArray = array()
        if (byteArray.size == remaining()) {
            addPosition(remaining())
            return byteArray
        }
    }
    return getBytes(remaining())
}

fun ByteBuffer.putIntLengthPrefixBytes(bytes: ByteArray): ByteBuffer {
    putInt(bytes.size)
    put(bytes)
    return this
}

fun ByteBuffer.getIntLengthPrefixBytes(): ByteArray = getBytes(getInt())

fun ByteBuffer.addPosition(delta: Int): ByteBuffer {
    // The nasty cast is to make this work under Java 11.
    (this as Buffer).position(position() + delta)
    return this
}

val ByteArray.intLengthPrefixSize: Int get() = Int.SIZE_BYTES + size

inline fun writeData(block: DataOutputStream.() -> Unit): ByteArray {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)
    block(dos)
    return baos.toByteArray()
}

fun ByteArray.dataStream(): DataInputStream = DataInputStream(inputStream())

inline fun <T> ByteArray.deserialise(block: DataInputStream.() -> T): T = block(dataStream())

/** Read and return exactly [n] bytes from the stream, or throw [EOFException]. */
fun DataInputStream.readExactlyNBytes(n: Int): ByteArray {
    val bytes = ByteArray(n)
    readFully(bytes)
    return bytes
}

fun DataInputStream.readIntLengthPrefixBytes(): ByteArray = readExactlyNBytes(readInt())

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
