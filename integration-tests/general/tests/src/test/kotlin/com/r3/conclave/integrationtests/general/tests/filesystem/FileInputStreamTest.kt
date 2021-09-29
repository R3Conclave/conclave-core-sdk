package com.r3.conclave.integrationtests.general.tests.filesystem

import com.r3.conclave.integrationtests.general.common.tasks.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.FileInputStream

class FileInputStreamTest : FileSystemEnclaveTest() {

    private inner class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            val reply = callEnclave(NewInputStream(path, uid, nioApi = false))
            assertThat(reply).startsWith(FileInputStream::class.java.name + "@")
        }

        fun readSingleByte() {
            callEnclave(ReadByteFromInputStream(uid))
        }

        fun readByteByByte(expectedData: ByteArray) {
            for (element in expectedData) {
                val reply = callEnclave(ReadByteFromInputStream(uid))
                assertThat(reply).isEqualTo(element.toInt())
            }

            // Test end of file
            val reply = callEnclave(ReadByteFromInputStream(uid))
            assertThat(reply).isEqualTo(-1)
        }

        fun readBytes(expectedData: ByteArray) {
            val reply = callEnclave(ReadAllBytesFromInputStream(uid))
            assertThat(reply).isEqualTo(expectedData)
        }

        fun isFdValid() {
            val reply = callEnclave(IsFileInputStreamFDValid(uid))
            assertThat(reply).isTrue
        }

        fun markNotAvailable() {
            val reply = callEnclave(IsInputStreamMarkSupported(uid))
            assertThat(reply).isFalse
        }

        override fun close() {
            callEnclave(CloseInputStream(uid))
        }
    }

    @Test
    fun writeReadDeleteFiles() {
        val smallFileData = byteArrayOf(1, 2, 3)
        val path = "/file.data"

        // Write the file and create a FileInputStream
        filesWrite(path, smallFileData)
        Handler(uid.getAndIncrement(), path).use { fis ->
            // Read by byte byte until EOF
            fis.readByteByByte(smallFileData)
            // Verify the file descriptor is valid
            fis.isFdValid()
            // Confirm mark is not available
            fis.markNotAvailable()
            // Close the file input stream
        }

        /*
         * Delete the file, ensure deleting it again and opening a FileInputStream
         * throws the expected exceptions.
         */
        filesDelete(path)
        filesDeleteNonExistingFile(path)
        fileInputStreamNonExistingFile(path)

        // Write the file again and readAllBytes at once
        filesWrite(path, smallFileData)
        Handler(uid.getAndIncrement(), path).use { fis ->
            fis.readBytes(smallFileData)
        }
        filesDelete(path)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/dev/random", "/dev/urandom"])
    fun readRandomDevices(device: String) {
        Handler(uid.getAndIncrement(), device).use { fis ->
            fis.readSingleByte()
        }
    }
}
