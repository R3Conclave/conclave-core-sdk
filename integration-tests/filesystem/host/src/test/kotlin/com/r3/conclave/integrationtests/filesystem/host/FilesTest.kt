package com.r3.conclave.integrationtests.filesystem.host

import com.r3.conclave.integrationtests.filesystem.host.FileInputStreamTest.Companion.fileInputStreamNonExistingFile
import com.google.protobuf.Int64Value
import com.r3.conclave.integrationtests.filesystem.common.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.Closeable
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.NoSuchFileException

class FilesTest : FileSystemEnclaveTest() {
    companion object {
        fun filesWrite(path: String, data: ByteArray) {
            val reply = request(type = Request.Type.FILES_WRITE, path = path, data = data)
            assertThat(String(reply!!)).isEqualTo(path)
        }

        fun filesReadAllBytes(path: String, data: ByteArray) {
            val reply = request(type = Request.Type.FILES_READ_ALL_BYTES, path = path)
            assertThat(reply).isEqualTo(data)
        }

        fun filesDelete(path: String) {
            val reply = request(type = Request.Type.FILES_DELETE, path = path)
            assertThat(reply).isEmpty()
        }

        fun filesDeleteNonExistingFile(path: String) {
            assertThatThrownBy { request(type = Request.Type.FILES_DELETE, path = path) }
                    .isInstanceOf(RuntimeException::class.java)
                    .hasCauseExactlyInstanceOf(NoSuchFileException::class.java)
                    .hasMessageContaining(path)
        }

        fun filesDeleteNonEmptyDir(path: String) {
            assertThatThrownBy { request(type = Request.Type.FILES_DELETE, path = path) }
                    .isInstanceOf(RuntimeException::class.java)
                    .hasCauseExactlyInstanceOf(DirectoryNotEmptyException::class.java)
                    .hasMessageContaining(path)
        }

        fun createDirectoryWithoutParent(path: String) {
            assertThatThrownBy { request(type = Request.Type.FILES_CREATE_DIRECTORY, path = path) }
                    .isInstanceOf(RuntimeException::class.java)
                    .hasCauseInstanceOf(IOException::class.java)
                    .hasMessageContaining(path)
        }

        fun createDirectory(path: String) {
            val reply = request(type = Request.Type.FILES_CREATE_DIRECTORY, path = path)
            assertThat(String(reply!!)).isEqualTo(path)
        }

        fun createDirectories(path: String) {
            val reply = request(type = Request.Type.FILES_CREATE_DIRECTORIES, path = path)
            assertThat(String(reply!!)).isEqualTo(path)
        }

        fun filesSize(path: String, expectedSize: Long) {
            val reply = request(type = Request.Type.FILES_SIZE, path = path)
            assertThat(Int64Value.parseFrom(reply).value).isEqualTo(expectedSize)
        }
    }

    private class Handler(private val uid: Int, path:String) : Closeable {
        init {
            val reply = request(type = Request.Type.FILES_NEW_INPUT_STREAM, uid = uid, path = path)
            assertThat(String(reply!!)).startsWith("com.r3.conclave.filesystem.jimfs.JimfsInputStream@")
        }

        fun readSingleByte() {
            val reply = request(type = Request.Type.INPUT_STREAM_READ, uid = uid)
            assertThat(reply!!.size).isEqualTo(1)
        }

        override fun close() {
            val reply = request(type = Request.Type.INPUT_STREAM_CLOSE, uid = uid)
            assertThat(reply).isEmpty()
        }
    }

    private class FilesNewOutputStreamHandler(private val uid: Int, path: String) : AutoCloseable {
        init {
            val reply = request(type = Request.Type.FILES_NEW_OUTPUT_STREAM_DELETE_ON_CLOSE, uid = uid, path = path)
            assertThat(String(reply!!)).startsWith("com.r3.conclave.filesystem.jimfs.JimfsOutputStream@")
        }

        override fun close() {
            val reply = request(type = Request.Type.OUTPUT_STREAM_CLOSE, uid = uid)
            assertThat(reply).isEmpty()
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

    @Disabled("Accessing random devices via Files.newInputStream is not supported")
    @ParameterizedTest
    @ValueSource(strings = ["/dev/random", "/dev/urandom"])
    fun readRandomDevice(device: String) {
        Handler(uid.getAndIncrement(), device).use {
            it.readSingleByte()
        }
    }

    @Disabled("DELETE_ON_CLOSE not supported")
    @Test
    fun outputStreamDeleteOnClose() {
        val path = "/fos-delete-on-close.data"
        FilesNewOutputStreamHandler(uid.getAndIncrement(), path).close()
        fileInputStreamNonExistingFile(path)
    }
}