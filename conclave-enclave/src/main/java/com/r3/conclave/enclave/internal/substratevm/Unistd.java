package com.r3.conclave.enclave.internal.substratevm;

import com.r3.conclave.filesystem.jimfs.JimfsInputStream;
import com.r3.conclave.filesystem.jimfs.JimfsOutputStream;
import com.r3.conclave.filesystem.jimfs.JimfsStream;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.io.IOException;
import java.io.InputStream;

/**
 * This class implements the native code being invoked from the substratevm unistd layer.
 * It relies on Graal's Native Image ability to implement native functions in Java.
 */
public class Unistd {

    enum Whence { SEEK_SET, SEEK_CUR, SEEK_END }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Unistd_read")
    public static int read(IsolateThread thread, int fd, CCharPointer buf, int count, CIntPointer errno) {
        final InputStream inputStream;
        if (!Fcntl.fileDescriptors.containsKey(fd) || (inputStream = (JimfsInputStream) Fcntl.fileDescriptors.get(fd)) == null) {
            errno.write(ErrnoBase.EBADF);
            return -1;
        }
        try {
            return readStreamAndWriteToBuffer(buf, inputStream, count);
        } catch (IOException e) {
            return -1;
        }
    }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Unistd_pread")
    public static int pread(IsolateThread thread, int fd, CCharPointer buf, int count, long offset, CIntPointer errno) {
        final JimfsInputStream inputStream;
        if (!Fcntl.fileDescriptors.containsKey(fd) || (inputStream = (JimfsInputStream) Fcntl.fileDescriptors.get(fd)) == null) {
            errno.write(ErrnoBase.EBADF);
            return -1;
        }
        if (offset < 0) {
            errno.write(ErrnoBase.EINVAL);
            return -1;
        }
        final long initialPosition = inputStream.position();
        inputStream.position(offset);
        try {
            return readStreamAndWriteToBuffer(buf, inputStream, count);
        } catch (IOException e) {
            return -1;
        } finally {
            inputStream.position(initialPosition);
        }
    }

    private static int readStreamAndWriteToBuffer(CCharPointer buf, InputStream inputStream, int count) throws IOException {
        byte[] byteArray = new byte[count];
        final int read = inputStream.read(byteArray);
        for (int i = 0; i < read; i++) {
            buf.write(i, byteArray[i]);
        }
        /*
         * The JDK is expecting 0 bytes to be returned from `read` to detect EOF, as opposed to InputStream.read
         * which returns -1 when EOF is reached.
         * See https://github.com/openjdk/jdk/blob/jdk8-b10/jdk/src/share/native/java/io/io_util.c#L48
         */
        return read == -1 ? 0 : read;
    }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Unistd_close")
    public static int close(IsolateThread thread, int fildes, CIntPointer errno) {
        final JimfsStream stream;
        if ((stream = Fcntl.fileDescriptors.remove(fildes)) == null) {
            errno.write(ErrnoBase.EBADF);
            return -1;
        }
        try {
            stream.close();
            return 0;
        } catch (IOException e) {
            errno.write(ErrnoBase.EIO);
            return -1;
        }
    }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Unistd_lseek64")
    public static long lseek64(IsolateThread thread, int fd, long offset, int whence, CIntPointer errno) {
        final JimfsStream stream;
        if (!Fcntl.fileDescriptors.containsKey(fd) || (stream = Fcntl.fileDescriptors.get(fd)) == null) {
            errno.write(ErrnoBase.EBADF);
            return -1;
        }
        final long initialPosition = stream.position();
        try {
            switch (Whence.values()[whence]) {
                case SEEK_SET:
                    stream.position(offset);
                    return offset;
                case SEEK_CUR:
                    stream.position(stream.position() + offset);
                    return stream.position();
                case SEEK_END:
                    stream.position(0);
                    final int endOfFile = stream.available();
                    final long newPosition = endOfFile + offset;
                    stream.position(newPosition);
                    return newPosition;
                default:
                    throw new IllegalArgumentException("Whence is not a proper value: " + whence + '.');
            }
        } catch (IllegalArgumentException e) {
            stream.position(initialPosition);
            errno.write(ErrnoBase.EINVAL);
            return -1;
        } catch (IOException e) {
            errno.write(ErrnoBase.EBADF);
            stream.position(initialPosition);
            return -1;
        }
    }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Unistd_write")
    public static int write(IsolateThread thread, int fd, CCharPointer buf, int count, CIntPointer errno) {
        final JimfsOutputStream outputStream;
        if (!Fcntl.fileDescriptors.containsKey(fd) || (outputStream = (JimfsOutputStream) Fcntl.fileDescriptors.get(fd)) == null) {
            errno.write(ErrnoBase.EBADF);
            return -1;
        }
        try {
            return readBufferAndWriteToStream(buf, count, outputStream);
        } catch (IOException e) {
            return -1;
        }
    }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Unistd_pwrite")
    public static int pwrite(IsolateThread thread, int fd, CCharPointer buf, int count, long offset, CIntPointer errno) {
        final JimfsOutputStream outputStream;
        if (!Fcntl.fileDescriptors.containsKey(fd) || (outputStream = (JimfsOutputStream) Fcntl.fileDescriptors.get(fd)) == null) {
            errno.write(ErrnoBase.EBADF);
            return -1;
        }
        if (offset < 0) {
            errno.write(ErrnoBase.EINVAL);
            return -1;
        }
        final long initialPosition = outputStream.position();
        outputStream.position(offset);
        try {
            return readBufferAndWriteToStream(buf, count, outputStream);
        } catch (IOException e) {
            return -1;
        } finally {
            outputStream.position(initialPosition);
        }
    }

    private static int readBufferAndWriteToStream(CCharPointer buf, int count, JimfsOutputStream outputStream) throws IOException {
        byte[] bytes = new byte[count];
        CTypeConversion.asByteBuffer(buf, count).get(bytes);
        outputStream.write(bytes, 0, count);
        return count;
    }
}
