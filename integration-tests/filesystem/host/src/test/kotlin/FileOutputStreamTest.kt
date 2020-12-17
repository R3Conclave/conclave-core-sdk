import FileInputStreamTest.Companion.fileInputStreamNonExistingFile
import FilesTest.Companion.filesDelete
import FilesTest.Companion.filesReadAllBytes
import com.r3.conclave.filesystem.proto.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.FileOutputStream

class FileOutputStreamTest : FileSystemEnclaveTest() {
    private class Handler(private val uid: Int, path: String, append: Boolean) : AutoCloseable {
        init {
            val reply = request(type = Request.Type.FILE_OUTPUT_STREAM_CONSTRUCTOR,
                    uid = uid,
                    path = path,
                    append = append)
            assertThat(String(reply!!)).startsWith(FileOutputStream::class.java.name + "@")
        }

        fun writeByteByByte(data: ByteArray) {
            for (element in data) {
                val reply = request(type = Request.Type.OUTPUT_STREAM_WRITE,
                        uid = uid,
                        data = byteArrayOf(element))
                assertThat(reply).isEmpty()
            }
        }

        fun writeBytes(data: ByteArray) {
            val reply = request(type = Request.Type.OUTPUT_STREAM_WRITE_BYTES,
                        uid = uid,
                        data = data)
            assertThat(reply).isEmpty()
        }

        fun writeBytesOffset(data: ByteArray, offset: Int, length: Int) {
            val reply = request(type = Request.Type.OUTPUT_STREAM_WRITE_OFFSET,
                    data = data,
                    uid = uid,
                    offset = offset,
                    length = length)
            assertThat(reply).isEmpty()
        }

        override fun close() {
            val reply = request(type = Request.Type.OUTPUT_STREAM_CLOSE, uid = uid)
            assertThat(reply).isEmpty()
        }
    }

    @Test
    fun fileOutputStreamWriteRead() {
        val path = "/fos.data"
        val fileData = byteArrayOf(1, 2, 3, 4)
        val reversedFileData = fileData.reversed().toByteArray()
        // Create a FileOutputStream and write the content byte by byte
        Handler(uid.getAndIncrement(), path, false).use { fos ->
            fos.writeByteByByte(fileData)
            // Close the FileOutputStream
        }
        // Read all bytes at once, delete the file and ensure opening it again fails with the expected exception
        filesReadAllBytes(path, fileData)
        filesDelete(path)
        fileInputStreamNonExistingFile(path)

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
        filesDelete(path)
        fileInputStreamNonExistingFile(path)
    }

    @Test
    fun fileOutputStreamAppendWrite() {
        val path = "/fos-append.data"
        val fileData = byteArrayOf(10, 20 , 30, 40)
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
        filesDelete(path)
        fileInputStreamNonExistingFile(path)
    }
}