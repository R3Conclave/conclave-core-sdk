package com.r3.conclave.integrationtests.djvm.enclave.sandbox

import com.r3.conclave.enclave.internal.memory.MemoryURLStreamHandler
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer

/**
 * URLStreamHandler used to keep the files sent by the host in memory
 */
object DJVMMemoryURLStreamHandler {
    private const val URL_SCHEME = "memory"

    private val memoryURLStreamHandler = MemoryURLStreamHandler(URL_SCHEME)
    private val handlers = listOf(memoryURLStreamHandler).associateBy { it.scheme }

    init {
        URL.setURLStreamHandlerFactory { protocol -> handlers[protocol] }
    }

    @Throws(MalformedURLException::class)
    fun createURL(path: String, data: ByteBuffer) = memoryURLStreamHandler.createURL(path, data)

    fun clear() = memoryURLStreamHandler.clear()

    val size get() = memoryURLStreamHandler.size
}