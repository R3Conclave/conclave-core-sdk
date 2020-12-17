import FilesTest.Companion.filesDelete
import FilesTest.Companion.filesDeleteNonExistingFile
import FilesTest.Companion.filesWrite
import com.r3.conclave.filesystem.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.FileInputStream
import java.io.FileNotFoundException

class FileInputStreamTest : FileSystemEnclaveTest() {

    companion object {
        fun fileInputStreamNonExistingFile(path: String) {
            assertThatThrownBy {
                request(type = Request.Type.FILE_INPUT_STREAM_CONSTRUCTOR,
                        uid = uid.getAndIncrement(),
                        path = path)
            }
                    .isInstanceOf(java.lang.RuntimeException::class.java)
                    .hasCauseExactlyInstanceOf(FileNotFoundException::class.java)
                    .hasMessageContaining(path)
        }
    }

    private class Handler(private val uid: Int, path: String) : AutoCloseable {
        init {
            val reply = request(type = Request.Type.FILE_INPUT_STREAM_CONSTRUCTOR,
                    uid = uid,
                    path = path)
            assertThat(String(reply!!)).startsWith(FileInputStream::class.java.name + "@")
        }

        fun readSingleByte() {
            val reply = request(type = Request.Type.INPUT_STREAM_READ, uid = uid)
            assertThat(reply!!.size).isEqualTo(1)
        }

        fun readByteByByte(expectedData: ByteArray) {
            for (element in expectedData) {
                val reply = request(type = Request.Type.INPUT_STREAM_READ, uid = uid)
                assertThat(reply!!.size).isEqualTo(1)
                assertThat(reply[0]).isEqualTo(element)
            }

            // Test end of file
            val reply = request(type = Request.Type.INPUT_STREAM_READ, uid = uid)
            assertThat(reply!!.size).isEqualTo(1)
            assertThat(reply[0]).isEqualTo(-1)
        }

        fun readBytes(expectedData: ByteArray) {
            val reply = request(type = Request.Type.INPUT_STREAM_READ_BYTES, uid = uid)
            assertThat(reply).isEqualTo(expectedData)
        }

        fun isFdValid() {
            val reply = request(type = Request.Type.FILE_INPUT_STREAM_FD, uid = uid)
            assertThat(reply!!.size).isEqualTo(1)
            assertThat(reply[0]).isEqualTo(1)
        }

        fun markNotAvailable() {
            val reply = request(type = Request.Type.INPUT_STREAM_MARK_AVAILABLE, uid = uid)
            assertThat(reply!!.size).isEqualTo(1)
            assertThat(reply[0]).isEqualTo(0)
        }

        override fun close() {
            val reply = request(type = Request.Type.INPUT_STREAM_CLOSE, uid = uid)
            assertThat(reply).isEmpty()
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
