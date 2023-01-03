package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.*
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.FileOutputStream

class FileOutputStreamTest : FileSystemEnclaveTest() {
    private inner class Handler(private val uid: Int, path: String, append: Boolean) : AutoCloseable {
        init {
            val reply = callEnclave(NewFileOutputStream(path, append, uid))
            assertThat(reply).startsWith(FileOutputStream::class.java.name + "@")
        }

        fun writeByteByByte(data: ByteArray) {
            for (element in data) {
                callEnclave(WriteByteToOutputStream(uid, element.toInt()))
            }
        }

        fun writeBytes(data: ByteArray) {
            callEnclave(WriteBytesToOutputStream(uid, data))
        }

        fun writeBytesOffset(data: ByteArray, offset: Int, length: Int) {
            callEnclave(WriteOffsetBytesToOutputStream(uid, data, offset, length))
        }

        override fun close() {
            callEnclave(CloseOutputStream(uid))
        }
    }

    @ParameterizedTest
    @CsvSource(
        "/fos.data, true",
        "/fos.data, false",
        "/tmp/fos.data, true",
        "/tmp/fos.data, false"
    )
    fun fileOutputStreamWriteRead(path: String, nioApi: Boolean) {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val fileData = byteArrayOf(1, 2, 3, 4)
        val reversedFileData = fileData.reversed().toByteArray()
        // Create a FileOutputStream and write the content byte by byte
        Handler(uid.getAndIncrement(), path, false).use { fos ->
            fos.writeByteByByte(fileData)
            // Close the FileOutputStream
        }
        // Read all bytes at once, delete the file and ensure opening it again fails with the expected exception
        filesReadAllBytes(path, fileData)
        deleteFile(path, nioApi)
        fileInputStreamNonExistingFile(path, nioApi)

        // Overwrite the file by writing a new one all at once
        Handler(uid.getAndIncrement(), path, false).use { fos ->
            fos.writeBytes(reversedFileData)
            filesReadAllBytes(path, reversedFileData)
        }

        // Overwrite the file by passing an array, offset and length
        val offset = 1
        val overwriteData = byteArrayOf(5, 6, 7)
        val expectedFileData = overwriteData.copyOfRange(offset, overwriteData.size)
        Handler(uid.getAndIncrement(), path, false).use { fos ->
            fos.writeBytesOffset(overwriteData, offset, overwriteData.size - offset)
        }
        // Read all bytes at once, delete the file and ensure opening it again fails with the expected exception
        filesReadAllBytes(path, expectedFileData)
        deleteFile(path, nioApi)
        fileInputStreamNonExistingFile(path, nioApi)
    }

    @ParameterizedTest
    @CsvSource(
        "/fos-append.data, true",
        "/fos-append.data, false",
        "/tmp/fos-append.data, true",
        "/tmp/fos-append.data, false"
    )
    fun fileOutputStreamAppendWrite(path: String, nioApi: Boolean) {
        graalvmOnlyTest() // CON-1264: Gramine: accessing filesystem and devices causes InvalidKeyException: Invalid AES key length: 0 bytes
        val fileData = byteArrayOf(10, 20, 30, 40)
        val appendData = byteArrayOf(50, 60, 70)
        // Create a FileOutputStream with append mode set
        Handler(uid.getAndIncrement(), path, true).use { fos ->
            fos.writeBytes(fileData)
        }
        filesReadAllBytes(path, fileData)

        // Append data to the file
        Handler(uid.getAndIncrement(), path, true).use { fos ->
            fos.writeBytes(appendData)
        }
        filesReadAllBytes(path, fileData + appendData)

        // Append data to the file by passing an array, offset and length
        val offset = 1
        val offsetData = byteArrayOf(80, 90, 100)
        val expectedData = fileData + appendData + offsetData.copyOfRange(offset, offsetData.size)
        Handler(uid.getAndIncrement(), path, true).use { fos ->
            fos.writeBytesOffset(offsetData, offset, offsetData.size - offset)
        }
        // Read all bytes at once, delete the file and ensure opening it again fails with the expected exception
        filesReadAllBytes(path, expectedData)
        deleteFile(path, nioApi)
        fileInputStreamNonExistingFile(path, nioApi)
    }
}
