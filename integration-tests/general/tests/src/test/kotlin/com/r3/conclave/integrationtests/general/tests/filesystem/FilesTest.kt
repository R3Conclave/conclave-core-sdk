package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.common.EnclaveMode.SIMULATION
import com.r3.conclave.host.EnclaveLoadException
import com.r3.conclave.integrationtests.general.common.tasks.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.Closeable
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.inputStream

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
        "/parent-dir",
        "/tmp/parent-dir",
    )
    fun listFilesMultipleTimes(dir: String) {
        val smallFileData = byteArrayOf(3, 2, 1)
        val path1 = "$dir/file1.data"
        val path2 = "$dir/file2.data"
        createDirectories(dir)

        filesWrite(path1, smallFileData)
        filesWrite(path2, smallFileData)

        val fileList1 = callEnclave(ListFilesNTimes(dir, 3))
        assertThat(fileList1).hasSize(3).containsOnly(listOf("file1.data", "file2.data"))

        val fileList2 = callEnclave(ListFilesNTimes(dir, 3))
        assertThat(fileList2).hasSize(3).containsOnly(listOf("file1.data", "file2.data"))
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

    @Test
    fun `an enclave with corrupted persistent filesystem fails`() {
        //  We want this test to run only in SIMULATION mode
        assumeTrue(SIMULATION.name == System.getProperty("enclaveMode").uppercase())

        copyCorruptedFileSystem()

        assertThatThrownBy {
            enclaveHost()
        }
            .isInstanceOf(EnclaveLoadException::class.java)
            .hasMessage("Unable to start enclave")
            .hasStackTraceContaining("java.io.IOException: Unable to initialize the enclave's persistent filesystem, potentially corrupted or unencryptable filesystem")
    }

    @ParameterizedTest
    @CsvSource(
        "/tmp/in-memory-fs-dir,/persistent-fs-dir/",
        "/persistent-fs-dir/,/tmp/in-memory-fs-dir"
    )
    fun `ensure moving directories between filesystems does not throw an error - empty directory`(src: String, dst: String) {
        createDirectory(src)

        assertDoesNotThrow {
            movePath(src, dst)
        }

        assertThat(callEnclave(FilesExists(src))).isFalse
        assertThat(callEnclave(FilesExists(dst))).isTrue
    }

    @ParameterizedTest
    @CsvSource(
        "/tmp/in-memory-fs-dir,/persistent-fs-dir/",
        "/persistent-fs-dir/,/tmp/in-memory-fs-dir"
    )
    fun `ensure moving directories between filesystems throws an error - not an empty directory`(src: String, dst: String) {
        createDirectory(src)
        createDirectory("$src/a")

        assertThatThrownBy {
            // Java does not allow moving directories that are not empty between filesystems.
            // Refer to: https://bugs.openjdk.org/browse/JDK-8201407
            movePath(src, dst)
        }.hasCauseExactlyInstanceOf(DirectoryNotEmptyException::class.java)
    }

    private fun copyCorruptedFileSystem() {
        val corruptedEnclaveResource =
            this::class.java.getResource("/com.r3.conclave.integrationtests.general.tests/corrupted_enclave_disk.gz")
        GZIPInputStream(Path.of(corruptedEnclaveResource!!.toURI()).inputStream()).use { input ->
            val enclaveFileSystemFile = getFileSystemFilePath(FILESYSTEM_ENCLAVE_CLASS_NAME)
            Files.copy(input, enclaveFileSystemFile!!)
        }
    }
}
