package com.r3.sgx.utils.classloaders

import java.io.IOException
import java.io.UncheckedIOException
import java.net.URL
import java.nio.ByteBuffer
import java.security.CodeSigner
import java.security.CodeSource
import java.security.SecureClassLoader
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.STORED
import java.util.zip.ZipInputStream

open class MemoryClassLoader(private val urls: List<MemoryURL>, parent: ClassLoader?) : SecureClassLoader(parent) {
    constructor(urls: List<MemoryURL>) : this(urls, ClassLoader.getSystemClassLoader())

    fun getURLs(): Array<URL> {
        return urls.map(MemoryURL::value).toTypedArray()
    }

    /**
     * Find the class byte-code within this [MemoryClassLoader] and
     * generate an actual class with it.
     */
    @Throws(ClassNotFoundException::class)
    final override fun findClass(name: String): Class<*> {
        val resourceName = name.replace('.', '/') + ".class"
        for (url in urls) {
            val connection = url.value.openConnection()
            ZipInputStream(connection.getInputStream()).use { jar ->
                while (true) {
                    val entry = jar.nextEntry ?: break
                    if (resourceName == entry.name) {
                        val byteCode = if (entry.isUncompressed && connection.content is ByteBuffer) {
                            (connection.content as ByteBuffer).slice().apply {
                                // Zero-copy optimisation for uncompressed data.
                                limit(entry.size.toInt())
                            }
                        } else {
                            ByteBuffer.wrap(jar.readBytes())
                        }
                        return defineClass(name, byteCode, url)
                    }
                }
            }
        }
        throw ClassNotFoundException(name)
    }

    private fun defineClass(name: String, byteCode: ByteBuffer, url: MemoryURL): Class<*> {
        val idx = name.lastIndexOf('.')
        if (idx > 0) {
            val packageName = name.substring(0, idx)
            if (getPackage(packageName) == null) {
                definePackage(packageName, null, null, null, null, null, null, null)
            }
        }
        return defineClass(name, byteCode, CodeSource(url.value, arrayOf<CodeSigner>()))
    }

    /**
     * Create a [URL] representing [resourceName] within our memory artifacts.
     */
    public final override fun findResource(resourceName: String): URL? {
        return try {
            resourcesFor(resourceName) { url, entry ->
                createURL(url.value, entry.name)
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    /**
     * Generate an [Enumeration] representing every resource matching [resourceName]
     * within our memory artifacts.
     */
    @Throws(IOException::class)
    public final override fun findResources(resourceName: String): Enumeration<URL> {
        val resources = mutableListOf<URL>()
        resourcesFor(resourceName) { url, entry ->
            resources.add(createURL(url.value, entry.name))
            null
        }

        return object : Enumeration<URL> {
            private val iterate: Iterator<URL> = resources.iterator()

            override fun hasMoreElements(): Boolean = iterate.hasNext()
            override fun nextElement(): URL = iterate.next()
        }
    }

    @Throws(IOException::class)
    private fun <T> resourcesFor(name: String, action: (MemoryURL, ZipEntry) -> T): T? {
        for (url in urls) {
            val connection = url.value.openConnection()
            ZipInputStream(connection.getInputStream()).use { jar ->
                while (true) {
                    val entry = jar.nextEntry ?: break
                    if (name == entry.name) {
                        action(url, entry)?.run {
                            return this
                        }
                    }
                }
            }
        }
        return null
    }

    private fun createURL(hostURL: URL, name: String): URL {
        return URL(hostURL.protocol, "", -1, hostURL.path + "!/" + name)
    }

    private val ZipEntry.isUncompressed: Boolean get() = method == STORED && size > 0
}
