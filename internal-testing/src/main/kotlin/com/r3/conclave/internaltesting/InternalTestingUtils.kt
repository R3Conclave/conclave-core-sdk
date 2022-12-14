package com.r3.conclave.internaltesting

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import kotlin.concurrent.thread
import kotlin.io.path.inputStream

/**
 * Executes the given block on a new thread, returning a [CompletableFuture] linked to the result. If [block] throws
 * an exception then the future completes with that exception.
 *
 * For testing purposes this is better than doing
 *
 * ```
 * thread { ... }.join()
 * ```
 *
 * since any exception thrown by the thread is not swallowed.
 */
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

fun JarInputStream.entries(): Sequence<JarEntry> = generateSequence { nextJarEntry }

fun Path.jarEntryNames(): List<String> {
    return JarInputStream(inputStream()).use {
        it.entries().map { it.name }.toList()
    }
}
