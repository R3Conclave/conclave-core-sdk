package com.r3.conclave.enclave.internal

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.dynamictesting.EnclaveBuilder
import com.r3.conclave.dynamictesting.TestEnclaves
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.host.internal.NativeEnclaveHandle
import java.io.Closeable

/**
 * With the intent of avoiding creating several enclaves, which is time-consuming, this object caches created enclaves
 * based on its class + handler instance + keyGenInput.
 * In order to do so, when [createOrGetEnclave] is called, it first checks for the cache, to see if there's anything
 * stored that matches the enclave class + handler instance + keyGenInput (which basically build a key).
 * If such enclave is not found, it creates the enclave from scratch and places it in the cache.
 * Otherwise, if the enclave is found, then it executes the handler's close().
 * the enclave for future usage (by other unit tests).
 */
object EnclaveRecycler {
    data class HandlerConnection(val handler: Any, val connection: Any)

    val enclaveCache = HashMap<String, HandlerConnection>()

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
 * Create or get existing enclave.
 * If there's an existing which suits the request the [CONNECTION] for it will be returned.
 * @param handler enclave handler to receive calls from TE. Upon reuse, and if it implements [Closeable],
 * [Closeable.close] will be called in to "clean-up" the handler before the [CONNECTION] is returned.
 * @param enclaveClass the java class containing the enclave implementation, e.g. GetSealingKeyEnclave::class.java.
 * @param enclaveBuilder (optional) enclave factory, [EnclaveBuilder] ()  by default.
 * @return a [CONNECTION] to the enclave.
 */
@Suppress("UNCHECKED_CAST")
fun <CONNECTION> TestEnclaves.createOrGetEnclave(
        handler: Handler<CONNECTION>,
        enclaveClass: Class<out Enclave>,
        enclaveBuilder: EnclaveBuilder = EnclaveBuilder(),
        keyGenInput: String? = null
): CONNECTION {
    val key = handler.toString() + enclaveClass.toString() + (keyGenInput ?: "")
    var enclave = EnclaveRecycler.enclaveCache[key]
    if (enclave != null) { // Is the enclave available in cache?
        if (enclave.handler is Closeable)
            (enclave.handler as Closeable).close() // clean the handle
    } else { // Create enclave if not.
        val enclaveFile = this.getSignedEnclaveFile(
                entryClass = enclaveClass,
                builder = enclaveBuilder,
                keyGenInput = keyGenInput)
                .toPath()
        enclave = EnclaveRecycler.HandlerConnection(
                handler = handler,
                connection = NativeEnclaveHandle(
                        EnclaveMode.SIMULATION,
                        enclaveFile,
                        false,
                        enclaveClass.name,
                        handler).let { it.connection as Any }
        )
        EnclaveRecycler.enclaveCache[key] = enclave
    }
    return enclave.connection as CONNECTION
}