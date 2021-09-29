package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.Closeable

class FilesTest : FileSystemEnclaveTest() {
    private inner class Handler(private val uid: Int, path:String) : Closeable {
        init {
            val reply = callEnclave(NewInputStream(path, uid, nioApi = true))
            assertThat(reply).startsWith("sun.nio.ch.ChannelInputStream")
        }

        fun readSingleByte() {
            callEnclave(ReadByteFromInputStream(uid))
        }

        override fun close() {
            callEnclave(CloseInputStream(uid))
        }
    }

    private inner class FilesNewOutputStreamHandler(private val uid: Int, path: String) : AutoCloseable {
        init {
            val reply = callEnclave(NewDeleteOnCloseOutputStream(path, uid))
            assertThat(reply).startsWith("java.nio.channels.Channels")
        }

        override fun close() {
            callEnclave(CloseOuputStream(uid))
        }
    }

    @Test
    fun createDeleteSingleDirectory() {
        val parent = "/parent-dir"
        val child = "$parent/child-dir"
        // Ensure creating a directory without creating its parent first fails with the expected exception
        createDirectoryWithoutParent(child)
        // Create parent and child directories
        createDirectory(parent)
        createDirectory(child)
        // Ensure deleting the parent directory before the child fails with the expected exception
        filesDeleteNonEmptyDir(parent)
        filesDelete(child)
        filesDelete(parent)
    }

    @Test
    fun createDeleteMultipleDirectories() {
        val smallFileData = byteArrayOf(3, 2, 1)
        val dir = "/multiple-dir/conclave-test"
        val path = "$dir/file.data"
        // Create parent and child directories at once
        createDirectories(dir)
        // Write and read a file from the child directory
        filesWrite(path, smallFileData)
        filesReadAllBytes(path, smallFileData)
        // Ensure deleting the child directory without deleting the file first fails with the expected exception
        filesDeleteNonEmptyDir(dir)
        // Delete the file and directory
        filesDelete(path)
        filesDelete(dir)
        // Ensure deleting or opening the non existing file fails with the expected exception
        filesDeleteNonExistingFile(path)
        fileInputStreamNonExistingFile(path)
    }

    @Test
    fun readBytes() {
        val path = "/readBytes.data"
        val smallFileData = byteArrayOf(1, 2, 3, 4)
        filesWrite(path, smallFileData)
        // Test Files.readAllBytes
        filesReadAllBytes(path, smallFileData)
        filesDelete(path)
        fileInputStreamNonExistingFile(path)
    }

    @Test
    fun fileSize() {
        val path = "/file-size.data"
        val smallFileData = byteArrayOf(4, 3, 2, 1)
        filesWrite(path, smallFileData)
        // Test Files.size
        filesSize(path, smallFileData.size.toLong())
        filesDelete(path)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/dev/random", "/dev/urandom"])
    fun readRandomDevice(device: String) {
        Handler(uid.getAndIncrement(), device).use {
            it.readSingleByte()
        }
    }

    @Test
    fun outputStreamDeleteOnClose() {
        val path = "/fos-delete-on-close.data"
        FilesNewOutputStreamHandler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path)
    }
}
