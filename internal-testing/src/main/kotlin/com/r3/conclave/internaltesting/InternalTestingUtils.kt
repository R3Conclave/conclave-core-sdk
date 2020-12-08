package com.r3.conclave.internaltesting

import org.assertj.core.api.Condition
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

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

private val mailCorruptionErrors = listOf(
        "Unknown Noise DH algorithm",
        "Unknown Noise cipher algorithm",
        "Unknown Noise hash algorithm",
        "Corrupt stream or not Conclave Mail",
        "Premature end of stream",
        "Protocol name must have 5 components",
        "Tag mismatch!",
        "Truncated Conclave Mail header",
        "SGX_ERROR_INVALID_CPUSVN",
        "SGX_ERROR_INVALID_ISVSVN",
)

val throwableWithMailCorruptionErrorMessage = object : Condition<Throwable>("a throwable containing a corruption error message") {
    override fun matches(value: Throwable?): Boolean {
        val match = generateSequence(value, Throwable::cause).any {
            val message = it.message
            message != null && mailCorruptionErrors.any { it in message }
        }
        if (!match) {
            value?.printStackTrace()
        }
        return match
    }
}
