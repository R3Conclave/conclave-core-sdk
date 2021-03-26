package com.r3.conclave.integrationtests.general.common.tasks

import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

fun <T> threadWithFuture(block: () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    thread {
        try {
            future.complete(block())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    return future
}

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).putInt(this).array()

fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).getInt()

inline fun writeData(block: DataOutputStream.() -> Unit): ByteArray {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)
    block(dos)
    return baos.toByteArray()
}

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

fun ByteArray.dataStream(): DataInputStream = DataInputStream(inputStream())

fun DataInputStream.readIntLengthPrefixBytes(): ByteArray = readExactlyNBytes(readInt())

fun DataOutputStream.writeIntLengthPrefixBytes(bytes: ByteArray) {
    writeInt(bytes.size)
    write(bytes)
}
