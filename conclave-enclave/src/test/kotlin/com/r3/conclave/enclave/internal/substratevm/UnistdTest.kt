package com.r3.conclave.enclave.internal.substratevm

import com.r3.conclave.enclave.internal.substratevm.mock.MockCCharPointer
import com.r3.conclave.enclave.internal.substratevm.mock.MockCIntPointer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class UnistdTest : JimfsTest() {

    companion object {
        @AfterAll
        @JvmStatic
        fun destroy() {
            assertThat(Fcntl.fileDescriptors).isEmpty()
        }
    }

    @BeforeEach
    fun createFile() {
        Paths.get(file).writeBytes(data)
    }

    @AfterEach
    fun closeFile() {
        if (Fcntl.isOpen(null, expectedFd)) {
            val errno = MockCIntPointer()
            Unistd.close(null, expectedFd, errno)
            assertThat(errno.value).isEqualTo(0)
        }
    }

    class FileOffsetProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            val range = 0..data.size.toLong()
            return range.toList().stream().map { Arguments.of(it) }
        }
    }

    @Test
    fun read() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val buf = MockCCharPointer(data.size)
        val read = Unistd.read(null, fd, buf, data.size, MockCIntPointer())
        assertThat(read).isEqualTo(data.size)
        assertThat(buf.byteArray).isEqualTo(data)
    }

    @Test
    fun readPastEndOfFile() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val mockCIntPointer = MockCIntPointer()
        val offset: Long = 1
        val position = Unistd.lseek64(null, fd, offset, Unistd.Whence.SEEK_END.ordinal, mockCIntPointer)
        assertThat(position).isEqualTo(data.size + offset)

        val buf = MockCCharPointer(data.size)
        val read = Unistd.read(null, fd, buf, data.size, mockCIntPointer)
        assertThat(read).isEqualTo(0)

        val currentPosition = Unistd.lseek64(null, fd, 0, Unistd.Whence.SEEK_CUR.ordinal, mockCIntPointer)
        assertThat(currentPosition).isEqualTo(position)
    }

    @Test
    fun readNonOpenFileDescriptor() {
        val fd = 0
        val count = 0
        val errno = MockCIntPointer()
        val ret = Unistd.read(null, fd, MockCCharPointer(file), count, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EBADF)
    }

    @ParameterizedTest
    @ArgumentsSource(FileOffsetProvider::class)
    fun pread(offset: Long) {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val buf = MockCCharPointer(data.size)
        val count = data.size - offset
        val read = Unistd.pread(null, fd, buf, count.toInt(), offset, MockCIntPointer())
        assertThat(read).isEqualTo(count)
        val expectedData = data.copyOfRange(offset.toInt(), data.size) + ByteArray((data.size - count).toInt())
        assertThat(buf.byteArray).isEqualTo(expectedData)
    }

    @Test
    fun preadDoesNotModifyStreamOffset() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val buf = MockCCharPointer(data.size)
        val count = 1
        val mockCIntPointer = MockCIntPointer()
        val firstRead = Unistd.read(null, fd, buf, count, mockCIntPointer)
        assertThat(firstRead).isEqualTo(count)
        assertThat(buf.byteArray.copyOfRange(0, count)).isEqualTo(data.copyOfRange(0, count))

        val offset = 2L
        val pread = Unistd.pread(null, fd, buf, count, offset, mockCIntPointer)
        assertThat(pread).isEqualTo(count)
        assertThat(buf.byteArray.copyOfRange(0, count)).isEqualTo(
            data.copyOfRange(
                offset.toInt(),
                (offset + count).toInt()
            )
        )

        val secondRead = Unistd.read(null, fd, buf, count, mockCIntPointer)
        assertThat(secondRead).isEqualTo(count)
        assertThat(buf.byteArray.copyOfRange(0, count)).isEqualTo(data.copyOfRange(firstRead, firstRead + count))
    }

    @Test
    fun preadNegativeOffset() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)
        val count = 0
        val offset = -2L
        val errno = MockCIntPointer()
        val ret = Unistd.pread(null, fd, MockCCharPointer(file), count, offset, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EINVAL)
    }

    @Test
    fun preadNonOpenFileDescriptor() {
        val fd = 0
        val count = 0
        val offset = 0L
        val errno = MockCIntPointer()
        val ret = Unistd.pread(null, fd, MockCCharPointer(file), count, offset, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EBADF)
    }

    @Test
    fun pwriteAllData() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val newValues = upcomingValues()
        val writtenBytes = Unistd.pwrite(null, fd, MockCCharPointer(file), data.size, 0, MockCIntPointer())
        assertThat(writtenBytes).isEqualTo(newValues.size.toLong())

        val readBytes = Paths.get(file).readBytes()
        assertThat(readBytes).isEqualTo(newValues)
    }

    @Test
    fun pwriteNonOpenFileDescriptor() {
        val fd = 0
        val count = 0
        val offset = 0L
        val errno = MockCIntPointer()
        val ret = Unistd.pwrite(null, fd, MockCCharPointer(file), count, offset, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EBADF)
    }

    @Test
    fun pwriteNegativeOffset() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val offset = -2L
        val errno = MockCIntPointer()
        val ret = Unistd.pwrite(null, fd, MockCCharPointer(file), 0, offset, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EINVAL)
    }

    @Test
    fun pwriteOffset() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val newValues = upcomingValues()
        val offset = 1L
        val writtenBytes = Unistd.pwrite(null, fd, MockCCharPointer(file), newValues.size, offset, MockCIntPointer())
        assertThat(writtenBytes).isEqualTo(newValues.size.toLong())

        val readBytes = Paths.get(file).readBytes()
        assertThat(readBytes).isEqualTo(data.copyOfRange(0, offset.toInt()) + newValues)
    }

    @Test
    fun pwriteAppend() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY).or(Fcntl.O_APPEND), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)
        val newValues = upcomingValues()
        val offset = 0L
        val writtenBytes = Unistd.pwrite(null, fd, MockCCharPointer(file), newValues.size, offset, MockCIntPointer())
        assertThat(writtenBytes).isEqualTo(newValues.size.toLong())

        val readBytes = Paths.get(file).readBytes()
        assertThat(readBytes).isEqualTo(data + newValues)
    }

    @Test
    fun pwriteAppendOffset() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY).or(Fcntl.O_APPEND), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val newValues = upcomingValues()
        val offset = 2L
        val writtenBytes = Unistd.pwrite(null, fd, MockCCharPointer(file), newValues.size, offset, MockCIntPointer())
        assertThat(writtenBytes).isEqualTo(newValues.size.toLong())

        val readBytes = Paths.get(file).readBytes()
        assertThat(readBytes).isEqualTo(data + newValues)
    }

    @Test
    fun write() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val newValues = upcomingValues()
        val written = Unistd.write(null, fd, MockCCharPointer(file), newValues.size, MockCIntPointer())
        assertThat(written).isEqualTo(newValues.size)

        val readBytes = Paths.get(file).readBytes()
        assertThat(readBytes).isEqualTo(newValues)
    }

    @Test
    fun writeAppend() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY).or(Fcntl.O_APPEND), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val newValues = upcomingValues()
        val written = Unistd.write(null, fd, MockCCharPointer(file), newValues.size, MockCIntPointer())
        assertThat(written).isEqualTo(newValues.size)

        val readBytes = Paths.get(file).readBytes()
        assertThat(readBytes).isEqualTo(data + newValues)
    }

    @Test
    fun writePastEndOfFile() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val mockCIntPointer = MockCIntPointer()
        val offset: Long = 2
        val position = Unistd.lseek64(null, fd, offset, Unistd.Whence.SEEK_END.ordinal, mockCIntPointer)
        assertThat(position).isEqualTo(data.size + offset)

        val newValues = upcomingValues()
        val written = Unistd.write(null, fd, MockCCharPointer(file), newValues.size, MockCIntPointer())
        assertThat(written).isEqualTo(newValues.size)

        val readAllBytes = Paths.get(file).readBytes()
        assertThat(readAllBytes).isEqualTo(data + ByteArray(offset.toInt()) + newValues)
    }

    @Test
    fun writeNonOpenFileDescriptor() {
        val fd = 0
        val count = 0
        val errno = MockCIntPointer()
        val ret = Unistd.write(null, fd, MockCCharPointer(file), count, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EBADF)
    }

    @ParameterizedTest
    @ValueSource(ints = [Fcntl.O_RDONLY, Fcntl.O_CREAT.or(Fcntl.O_WRONLY)])
    fun lseekStreams(whence: Int) {
        val fd = Fcntl.open(null, MockCCharPointer(file), whence, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val mockCIntPointer = MockCIntPointer()
        val offset: Long = 1
        val position = Unistd.lseek64(null, fd, offset, Unistd.Whence.SEEK_SET.ordinal, mockCIntPointer)
        assertThat(position).isEqualTo(offset)

        val newPosition = Unistd.lseek64(null, fd, offset, Unistd.Whence.SEEK_CUR.ordinal, mockCIntPointer)
        assertThat(newPosition).isEqualTo(position + offset)

        val endOfFile = Unistd.lseek64(null, fd, 0, Unistd.Whence.SEEK_END.ordinal, mockCIntPointer)
        assertThat(endOfFile).isEqualTo(data.size.toLong())

        val pastEndOfFile = Unistd.lseek64(null, fd, 1, Unistd.Whence.SEEK_END.ordinal, mockCIntPointer)
        assertThat(pastEndOfFile).isEqualTo(endOfFile + 1)
    }

    @Test
    fun lseekRead() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val mockCIntPointer = MockCIntPointer()
        val offset: Long = 1
        val position = Unistd.lseek64(null, fd, offset, Unistd.Whence.SEEK_SET.ordinal, mockCIntPointer)
        assertThat(position)

        val count = 1
        val buf = MockCCharPointer(count)
        var read = Unistd.read(null, fd, buf, count, mockCIntPointer)
        assertThat(read).isEqualTo(count)
        assertThat(buf.byteArray.copyOfRange(0, count)).isEqualTo(
            data.copyOfRange(
                position.toInt(),
                (position + count).toInt()
            )
        )

        val currentPosition = Unistd.lseek64(null, fd, 0, Unistd.Whence.SEEK_CUR.ordinal, mockCIntPointer)
        assertThat(currentPosition).isEqualTo(position + count)

        val endOfFile = Unistd.lseek64(null, fd, 0, Unistd.Whence.SEEK_END.ordinal, mockCIntPointer)
        assertThat(endOfFile).isEqualTo(data.size.toLong())

        val pastEndOfFile = Unistd.lseek64(null, fd, offset, Unistd.Whence.SEEK_END.ordinal, mockCIntPointer)
        assertThat(pastEndOfFile).isEqualTo(endOfFile + offset)

        read = Unistd.read(null, fd, buf, count, mockCIntPointer)
        assertThat(read).isEqualTo(0)

        val positionAfterRead = Unistd.lseek64(null, fd, 0, Unistd.Whence.SEEK_CUR.ordinal, mockCIntPointer)
        assertThat(positionAfterRead).isEqualTo(pastEndOfFile)
    }

    @Test
    fun lseekWrite() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_CREAT.or(Fcntl.O_WRONLY), expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val readFd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd + 1)
        assertThat(readFd).isEqualTo(expectedFd + 1)

        val newValues = upcomingValues()
        val count = 1
        val buf = MockCCharPointer(count)
        val written = Unistd.write(null, fd, buf, count, MockCIntPointer())
        assertThat(written).isEqualTo(count)

        val errno = MockCIntPointer()
        val position = Unistd.lseek64(null, fd, 0, Unistd.Whence.SEEK_CUR.ordinal, errno)
        assertThat(position).isEqualTo(count.toLong())

        val offset: Long = -1
        val rewind = Unistd.lseek64(null, readFd, position + offset, Unistd.Whence.SEEK_SET.ordinal, errno)
        assertThat(rewind).isEqualTo(position + offset)

        val read = Unistd.read(null, readFd, buf, count, errno)
        assertThat(read).isEqualTo(count.toLong())
        assertThat(buf.byteArray.copyOfRange(0, count)).isEqualTo(newValues.copyOfRange(0, count))

        Unistd.close(null, readFd, errno)
        assertThat(errno.value).isEqualTo(0)
    }

    @Test
    fun lseekNegativeOffset() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)

        val offset: Long = -2
        val errno = MockCIntPointer()
        val ret = Unistd.lseek64(null, fd, offset, Unistd.Whence.SEEK_CUR.ordinal, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EINVAL)
    }

    @ParameterizedTest
    @ValueSource(ints = [Fcntl.O_RDONLY, Fcntl.O_CREAT.or(Fcntl.O_WRONLY)])
    fun lseekNonOpenFileDescriptor(whence: Int) {
        val fd = 0
        val offset: Long = 0
        val errno = MockCIntPointer()
        val ret = Unistd.lseek64(null, fd, offset, whence, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EBADF)
    }

    @Test
    fun closeNonOpenFileDescriptor() {
        val fd = 0
        val errno = MockCIntPointer()
        val ret = Unistd.close(null, fd, errno)
        assertThat(ret).isEqualTo(-1)
        assertThat(errno.value).isEqualTo(ErrnoBase.EBADF)
    }

    @Test
    fun closeDeletesFileDescriptorEntry() {
        val fd = Fcntl.open(null, MockCCharPointer(file), Fcntl.O_RDONLY, expectedFd)
        assertThat(fd).isEqualTo(expectedFd)
        val errno = MockCIntPointer()
        Unistd.close(null, expectedFd, errno)
        assertThat(errno.value).isEqualTo(0)
        assertThat(Fcntl.fileDescriptors).doesNotContainKey(fd)
    }
}
