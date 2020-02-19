package com.r3.sgx.test.enclave.djvm.url

import com.r3.sgx.utils.classloaders.MemoryURLStreamHandler
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer

/**
 * URLStreamHandler used to keep the files sent by the host in memory
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