package com.r3.conclave.integrationtests.general.common

import com.r3.conclave.common.internal.Cursor
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

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()
fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).getInt()

typealias ByteCursor<T> = Cursor<T, ByteBuffer>
