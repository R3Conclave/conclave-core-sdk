package com.r3.conclave.utils.classloaders

import com.r3.conclave.common.internal.ByteBufferInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import java.util.zip.ZipInputStream

/**
 * URL for "memory:/" scheme, and a strong reference for [ByteBuffer]'s
 * key inside our [WeakHashMap]. We need to hold at least one strong
 * reference to this key to prevent the garbage collector from deleting
 * the [ByteBuffer] from our [WeakHashMap].
 */
data class MemoryURL(val value: URL, val lock: String)

/**
 * Implements a [URLStreamHandler] that supports [MemoryURL] objects.
 * This handler must be installed using [URL.setURLStreamHandlerFactory]
 * before it can be used. However, be warned that a JVM will only allow
 * you to invoke [URL.setURLStreamHandlerFactory] at most once.
 * @property scheme The protocol name that identifies this URL type.
 */
class MemoryURLStreamHandler(val scheme: String) : URLStreamHandler() {
    private val dataCache: MutableMap<String, ByteBuffer> = WeakHashMap()

    /**
     * Creates a new entry in the [WeakHashMap] that underlies "memory:/" URLs,
     * or throws an exception if this URL has already been defined.
     * @return a [MemoryURL] containing both the new URL and a lock value that
     * holds a strong reference to the [ByteBuffer] inside the map.
     */
    @Throws(MalformedURLException::class)
    fun createURL(path: String, data: ByteBuffer): MemoryURL {
        if (path.contains("!/")) {
            throw MalformedURLException("Invalid URL path '$path'")
        }

        val url = URL(scheme, "", -1, path, this)
        val lock = url.toString()
        if (dataCache.putIfAbsent(lock, data.asReadOnlySlice()) != null) {
            throw MalformedURLException("URL '$lock' already exists")
        }
        return MemoryURL(url, lock)
    }

    // Discard everything before this buffer's current position.
    private fun ByteBuffer.asReadOnlySlice(): ByteBuffer {
        return (if (isReadOnly) this else asReadOnlyBuffer()).slice()
    }

    fun clear() {
        dataCache.clear()
    }

    val size: Int get() = dataCache.size

    @Throws(IOException::class)
    override fun openConnection(url: URL): URLConnection {
        val urlValue = url.toString().split("!/")
        // The URL we just split might be for multi-release JAR followed with a #[type]. Remove
        // the hash and the part after it.
        val urlValueMulti = urlValue.first.split("#")

        val data = dataCache[urlValueMulti.first] ?: throw IOException("No data for URL '${urlValue.first}'")
        return Connection(url, urlValue.second, data.duplicate())
    }

    private fun String.split(delimiter: String): Pair<String, String> {
        val idx = indexOf(delimiter)
        return if (idx == -1) {
            Pair(this, "")
        } else {
            Pair(substring(0, idx), substring(idx + delimiter.length, length))
        }
    }

    private class Connection(url: URL, private val path: String, private val data: ByteBuffer) : URLConnection(url) {
        private val handler = ConnectionHandler()

        init {
            allowUserInteraction = false
            useCaches = false
        }

        @Throws(IOException::class)
        @Synchronized
        override fun connect() {
            if (!connected) {
                handler.connect()
                connected = true
            }
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            connect()
            return handler
        }

        @Throws(IOException::class)
        override fun getContentLengthLong(): Long {
            connect()
            return handler.dataSize
        }

        @Throws(IOException::class)
        override fun getContent(): ByteBuffer {
            connect()
            if (path.isNotEmpty()) {
                throw UnsupportedOperationException("Not supported for $url")
            }
            return data
        }

        @Throws(IOException::class)
        override fun getContentType(): String {
            connect()
            return "application/octet-stream"
        }

        override fun setDoOutput(doOutput: Boolean) = throw UnsupportedOperationException("Output not supported")
        override fun setAllowUserInteraction(allowUserInterfaction: Boolean) = throw UnsupportedOperationException("User interaction not supported")

        private inner class ConnectionHandler : InputStream() {
            @Volatile
            private var dataStream: InputStream = ByteBufferInputStream(data)

            @Volatile
            var dataSize: Long = data.limit().toLong()
                private set

            @Throws(FileNotFoundException::class)
            fun connect() {
                if (path.isNotEmpty()) {
                    val zipStream = ZipInputStream(dataStream)
                    while (true) {
                        val entry = zipStream.nextEntry ?: throw FileNotFoundException(path)
                        if (path == entry.name) {
                            dataStream = zipStream
                            dataSize = entry.size
                            break
                        }
                    }
                }
            }

            @Throws(IOException::class)
            override fun read(): Int = dataStream.read()

            @Throws(IOException::class)
            override fun read(buffer: ByteArray, offset: Int, size: Int): Int = dataStream.read(buffer, offset, size)

            @Throws(IOException::class)
            override fun skip(size: Long): Long = dataStream.skip(size)

            override fun available(): Int = dataStream.available()

            @Throws(IOException::class)
            override fun close() = dataStream.close()
        }
    }
}
