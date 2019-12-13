package com.r3.sgx.multiplex.enclave

import com.r3.sgx.utils.classloaders.MemoryURLStreamHandler
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer

/**
 * Create and install a [MemoryURLStreamHandler] for multiplex
 * enclaves to use. Multiplex URLs will have "multi:/" scheme.
 */
object URLSchemes {
    private const val MULTIPLEX_SCHEME = "multi"

    private val multiplex = MemoryURLStreamHandler(MULTIPLEX_SCHEME)
    private val handlers = listOf(multiplex)
        .associateBy(MemoryURLStreamHandler::scheme)

    init {
        URL.setURLStreamHandlerFactory { protocol -> handlers[protocol] }
    }

    @Throws(MalformedURLException::class)
    fun createMultiplexURL(path: String, data: ByteBuffer) = multiplex.createURL(path, data)

    val size get() = multiplex.size

    internal fun clearURLs() {
        multiplex.clear()
    }
}
