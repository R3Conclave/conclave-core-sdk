package com.r3.conclave.enclave.internal.memory

import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer

/**
 * Create and install a [MemoryURLStreamHandler] instance that we can test.
 */
object URLSchemes {
    private const val MEMORY_SCHEME = "memory"

    private val memory = MemoryURLStreamHandler(MEMORY_SCHEME)
    private val handlers = listOf(memory)
        .associateBy(MemoryURLStreamHandler::scheme)

    init {
        URL.setURLStreamHandlerFactory { protocol -> handlers[protocol] }
    }

    @Throws(MalformedURLException::class)
    fun createMemoryURL(path: String, data: ByteBuffer) = memory.createURL(path, data)

    val size get() = memory.size

    fun clearURLs() {
        memory.clear()
    }
}

