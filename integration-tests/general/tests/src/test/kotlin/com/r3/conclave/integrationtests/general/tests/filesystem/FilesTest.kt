package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.Closeable

class FilesTest : FileSystemEnclaveTest() {
    private inner class Handler(private val uid: Int, path: String) : Closeable {
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
            callEnclave(CloseOutputStream(uid))
        }
    }

    @ParameterizedTest
    @CsvSource(
        "/parent-dir, true",
        "/parent-dir, false",
        "/tmp/parent-dir, true",
        "/tmp/parent-dir, false"
    )
    fun createDeleteSingleDirectory(parent: String, nioApi: Boolean) {
        val child = "$parent/child-dir"
        // Ensure creating a directory without creating its parent first fails with the expected exception
        createDirectoryWithoutParent(child)
        // Create parent and child directories
        createDirectory(parent)
        createDirectory(child)
        // Ensure deleting the parent directory before the child fails with the expected exception
        filesDeleteNonEmptyDir(parent, nioApi)
        deleteFile(child, nioApi)
        deleteFile(parent, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/multiple-dir/conclave-test, true",
        "/multiple-dir/conclave-test, false",
        "/tmp/multiple-dir/conclave-test, true",
        "/tmp/multiple-dir/conclave-test, false"
    )
    fun createDeleteMultipleDirectories(dir: String, nioApi: Boolean) {
        val smallFileData = byteArrayOf(3, 2, 1)
        val path = "$dir/file.data"
        // Create parent and child directories at once
        createDirectories(dir)
        // Write and read a file from the child directory
        filesWrite(path, smallFileData)
        filesReadAllBytes(path, smallFileData)
        // Ensure deleting the child directory without deleting the file first fails with the expected exception
        filesDeleteNonEmptyDir(dir, nioApi)
        // Delete the file and directory
        deleteFile(path, nioApi)
        deleteFile(dir, nioApi)
        // Ensure deleting or opening the non existing file fails with the expected exception
        filesDeleteNonExistingFile(path, nioApi)
        fileInputStreamNonExistingFile(path, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/parent-dir",
        "/tmp/parent-dir",
    )
    fun createDeleteNestedDirectories(parent: String) {
        val path = "$parent/child-dir/grandchild-dir"
        callEnclave(WalkAndDelete(path))
        assertThat(callEnclave(FilesExists(path))).isFalse
    }

    @ParameterizedTest
    @CsvSource(
        "/readBytes.data, true",
        "/readBytes.data, false",
        "/tmp/readBytes.data, true",
        "/tmp/readBytes.data, false"
    )
    fun readBytes(path: String, nioApi: Boolean) {
        val smallFileData = byteArrayOf(1, 2, 3, 4)
        filesWrite(path, smallFileData)
        // Test Files.readAllBytes
        filesReadAllBytes(path, smallFileData)
        deleteFile(path, nioApi)
        fileInputStreamNonExistingFile(path, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/file-size.data, true",
        "/file-size.data, false",
        "/tmp/file-size.data, true",
        "/tmp/file-size.data, false"
    )
    fun fileSize(path: String, nioApi: Boolean) {
        val smallFileData = byteArrayOf(4, 3, 2, 1)
        filesWrite(path, smallFileData)
        // Test Files.size
        filesSize(path, smallFileData.size.toLong())
        deleteFile(path, nioApi)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/dev/random", "/dev/urandom"])
    fun readRandomDevice(device: String) {
        Handler(uid.getAndIncrement(), device).use {
            it.readSingleByte()
        }
    }

    @ParameterizedTest
    @CsvSource(
        "/fos-delete-on-close.data, true",
        "/fos-delete-on-close.data, false",
        "/tmp/fos-delete-on-close.data, true",
        "/tmp/fos-delete-on-close.data, false"
    )
    fun outputStreamDeleteOnClose(path: String, nioApi: Boolean) {
        FilesNewOutputStreamHandler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path, nioApi)
    }
}
