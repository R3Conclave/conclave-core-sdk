package com.r3.conclave.samples.djvm.enclave

import com.r3.conclave.utils.classloaders.MemoryURLStreamHandler
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer

/**
 * In memory URL stream handler for @see [DJVMMemoryURLStreamHandler.URL_SCHEME]
 */
object DJVMMemoryURLStreamHandler {
    private const val URL_SCHEME = "memory"

    private val memoryURLStreamHandler = MemoryURLStreamHandler(URL_SCHEME)
    private val handlers = listOf(memoryURLStreamHandler)
            .associateBy(MemoryURLStreamHandler::scheme)

    init {
        URL.setURLStreamHandlerFactory { protocol -> handlers[protocol] }
    }

    @Throws(MalformedURLException::class)
    fun createURL(path: String, data: ByteBuffer) = memoryURLStreamHandler.createURL(path, data)

    fun clear() = memoryURLStreamHandler.clear()

    val size get() = memoryURLStreamHandler.size
}