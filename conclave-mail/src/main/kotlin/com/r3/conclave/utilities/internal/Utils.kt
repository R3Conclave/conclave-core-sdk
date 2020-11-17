package com.r3.conclave.utilities.internal

import java.io.*
import java.nio.Buffer
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.cert.CertPath
import java.security.cert.X509Certificate

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

fun ByteBuffer.getUnsignedShort(): Int = java.lang.Short.toUnsignedInt(getShort())

fun ByteBuffer.getUnsignedShort(index: Int): Int = java.lang.Short.toUnsignedInt(getShort(index))

fun ByteBuffer.putUnsignedShort(value: Int): ByteBuffer {
    require(value >= 0 && value <= 65535) { "Not an unsigned short: $value" }
    return putShort(value.toShort())
}

fun ByteBuffer.getUnsignedInt(): Long = Integer.toUnsignedLong(getInt())

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

/**
 * Creates a slice of the next [size] bytes. The position is advanced by [size].
 */
fun ByteBuffer.getSlice(size: Int): ByteBuffer {
    if (size > remaining()) throw BufferUnderflowException()
    val slice = slice()
    (slice as Buffer).limit(size)
    addPosition(size)
    return slice
}

/**
 * Return a slice, of the given [size], out of the buffer at the given [index]. The receiver buffer is unchanged.
 *
 * The sliced buffer will have a position zero and a limit of [size].
 */
fun ByteBuffer.getSlice(size: Int, index: Int): ByteBuffer {
    val duplicate = duplicate()
    (duplicate as Buffer).position(index)
    (duplicate as Buffer).limit(index + size)
    return duplicate.slice()
}

/**
 * Create a slice out of the buffer of size defined by the next int in the buffer. The slice immediately follows the size
 * field. The position is advanced by [Int.SIZE_BYTES] plus the size of the slice itself.
 */
fun ByteBuffer.getIntLengthPrefixSlice(): ByteBuffer = getSlice(getInt())

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
fun InputStream.readExactlyNBytes(n: Int): ByteArray = ByteArray(n).also { readExactlyNBytes(it, n) }

fun InputStream.readExactlyNBytes(buffer: ByteArray, n: Int) {
    require(n >= 0)
    var cursor = 0
    while (cursor < n) {
        val count = read(buffer, cursor, n - cursor)
        if (count < 0) throw EOFException()
        cursor += count
    }
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

val CertPath.x509Certs: List<X509Certificate> get() {
    check(type == "X.509") { "Not an X.509 cert path: $type" }
    @Suppress("UNCHECKED_CAST")
    return certificates as List<X509Certificate>
}