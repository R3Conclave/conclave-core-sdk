package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.NativeEnclaveHandle
import com.r3.conclave.internaltesting.dynamic.EnclaveBuilder
import com.r3.conclave.internaltesting.dynamic.TestEnclaves
import java.io.Closeable

/**
 * With the intent of avoiding creating several enclaves, which is time-consuming, this object caches created enclaves
 * based on its class + handler instance + keyGenInput.
 * In order to do so, when [createOrGetEnclaveConnection] is called, it first checks for the cache, to see if there's anything
 * stored that matches the enclave class + handler instance + keyGenInput (which basically build a key).
 * If such enclave is not found, it creates the enclave from scratch and places it in the cache.
 * Otherwise, if the enclave is found, then it executes the handler's close().
 * the enclave for future usage (by other unit tests).
 */
object EnclaveRecycler {
    val enclaveCache = HashMap<String, HandlerConnected<*>>()

    /**
     * Clears the enclave cache. During unit tests, it should be used in the @AfterAll method.
     */
    fun clear() = enclaveCache.clear()
}

// Note on the function TestEnclaves.createOrGetEnclave, to suppress (the ugly) "...::class.java" in "enclaveClass",
// the following reified version was considered:
//
// inline fun <reified T : Enclave, CONNECTION> TestEnclaves.createOrGetEnclave(
//        handler: Handler<CONNECTION>,
//        noinline recycler: (() -> Unit)? = null,
//        enclaveBuilder: EnclaveBuilder = EnclaveBuilder(),
//        keyGenInput: String? = null
// ): CONNECTION
//
// But, as "partial inference" doesn't exist yet in Kotlin, the caller would be forced to include "CONNECTION" (which is
// currently being inferred from "handler"). As a result it would have "cost" one more parameter.

/**
 * Create or get existing handler connection.
 * If there's an existing which suits the request the [CONNECTION] for it will be returned.
 * @param handler enclave handler to receive calls from TE. Upon reuse, and if it implements [Closeable],
 * [Closeable.close] will be called in to "clean-up" the handler before the [CONNECTION] is returned.
 * @param enclaveClass the java class containing the enclave implementation, e.g. GetSealingKeyEnclave::class.java.
 * @param enclaveBuilder (optional) enclave factory, [EnclaveBuilder] ()  by default.
 * @return a [CONNECTION] for the handler.
 */
fun <CONNECTION> TestEnclaves.createOrGetEnclaveConnection(
    handler: Handler<CONNECTION>,
    enclaveClass: Class<out Enclave>,
    enclaveBuilder: EnclaveBuilder = EnclaveBuilder(),
    keyGenInput: String? = null
): CONNECTION {
    val key = "$handler$enclaveClass${keyGenInput ?: ""}"
    var handlerConnected = EnclaveRecycler.enclaveCache[key]
    if (handlerConnected != null) { // Is the enclave available in cache?
        (handlerConnected.handler as? Closeable)?.close()
    } else { // Create enclave if not.
        val enclaveFile = this.getSignedEnclaveFile(
            entryClass = enclaveClass,
            builder = enclaveBuilder,
            keyGenInput = keyGenInput
        ).toPath()
        handlerConnected = HandlerConnected(
            handler = handler,
            connection = NativeEnclaveHandle(
                EnclaveMode.SIMULATION,
                enclaveFile,
                false,
                enclaveClass.name,
                handler
            ).connection
        )
        EnclaveRecycler.enclaveCache[key] = handlerConnected
    }
    @Suppress("UNCHECKED_CAST")
    return handlerConnected.connection as CONNECTION
}
