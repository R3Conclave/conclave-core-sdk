package com.r3.sgx.djvm

import org.assertj.core.api.Assertions.assertThat
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Attributes.Name.MANIFEST_VERSION
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.DEFLATED
import java.util.zip.ZipEntry.STORED

/**
 * Factory object for creating a dummy jar with a basic manifest.
 */
class DummyJar(
        private val projectDir: Path,
        private val name: String
) {
    companion object {
        private fun uncompressed(name: String, data: ByteArray) = ZipEntry(name).apply {
            method = STORED
            compressedSize = data.size.toLong()
            size = data.size.toLong()
            crc = CRC32().let { crc ->
                crc.update(data)
                crc.value
            }
        }

        private fun compressed(name: String) = ZipEntry(name).apply { method = DEFLATED }

        @Throws(IOException::class)
        @JvmStatic
        fun JarOutputStream.putCompressedClass(clazz: Class<*>) {
            putCompressedEntry(clazz.resourceName, clazz.bytecode)
        }

        @Throws(IOException::class)
        @JvmStatic
        fun JarOutputStream.putCompressedEntry(name: String, data: ByteArray) {
            putNextEntry(compressed(name))
            write(data)
        }

        @Throws(IOException::class)
        @JvmStatic
        fun JarOutputStream.putUncompressedEntry(name: String, data: ByteArray) {
            putNextEntry(uncompressed(name, data))
            write(data)
        }

        @Throws(IOException::class)
        @JvmStatic
        fun JarOutputStream.putDirectoryOf(clazz: Class<*>) {
            putNextEntry(directoryOf(clazz))
        }

        @JvmStatic
        fun directoryOf(type: Class<*>)
                = directory(type.`package`.name.toPathFormat + '/')

        private fun directory(name: String) = ZipEntry(name).apply {
            method = STORED
            compressedSize = 0
            size = 0
            crc = 0
        }

        fun Path.pathOf(vararg elements: String): Path = Paths.get(toAbsolutePath().toString(), *elements)

        private val String.toPathFormat: String get() = replace('.', '/')
        private val String.resourceName: String get() = "$toPathFormat.class"

        @JvmStatic
        val Class<*>.resourceName: String get() = name.resourceName

        @JvmStatic
        val Class<*>.bytecode: ByteArray
            @Throws(FileNotFoundException::class)
            get() = classLoader.getResourceAsStream(resourceName)?.use {
                it.readBytes()
            } ?: throw FileNotFoundException("Cannot find $resourceName")

        @JvmStatic
        fun arrayOfJunk(size: Int): ByteArray = ByteArray(size).apply {
            for (i in indices) {
                this[i] = (i and 0xFF).toByte()
            }
        }
    }

    private lateinit var _path: Path
    val path: Path get() = _path

    @Throws(IOException::class)
    fun build(writer: JarWriter): DummyJar {
        val manifest = Manifest().apply {
            mainAttributes.also { main ->
                main[MANIFEST_VERSION] = "1.0"
            }
        }
        _path = projectDir.pathOf("$name.jar")
        JarOutputStream(Files.newOutputStream(_path), manifest).use { jar ->
            writer.write(jar, _path)
        }
        assertThat(_path).isRegularFile()
        return this
    }
}
