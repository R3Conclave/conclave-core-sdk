package com.r3.conclave.enclave.internal.substratevm;

import com.r3.conclave.filesystem.jimfs.JimfsStream;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the native code being invoked from the substratevm fcntl layer.
 * It relies on Graal's Native Image ability to implement native functions in Java.
 */
public class Fcntl {

    // Flags in octal
    public static final int O_RDONLY = 00;
    public static final int O_WRONLY = 01;
    public static final int O_CREAT = 0100;
    public static final int O_TRUNC = 01000;
    public static final int O_APPEND = 02000;

    public static final ConcurrentHashMap<Integer, JimfsStream> fileDescriptors = new ConcurrentHashMap<>();

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Fcntl_open")
    public static int open(IsolateThread thread, CCharPointer input, int oflag, int fd) {
        try {
            final String file = CTypeConversion.toJavaString(input);
            if (file.startsWith("/sys")) {
                return -1;
            }
            switch (oflag & O_WRONLY) {
                case O_RDONLY:
                    fileDescriptors.put(fd, (JimfsStream) Files.newInputStream(Paths.get(file), StandardOpenOption.READ));
                    break;
                case O_WRONLY:
                    final EnumSet<StandardOpenOption> openOptions = EnumSet.of(StandardOpenOption.WRITE);
                    if ((oflag & O_APPEND) != 0) {
                        openOptions.add(StandardOpenOption.APPEND);
                    }
                    if ((oflag & O_CREAT) != 0) {
                        openOptions.add(StandardOpenOption.CREATE);
                    }
                    if ((oflag & O_TRUNC) != 0) {
                        openOptions.add(StandardOpenOption.TRUNCATE_EXISTING);
                    }
                    final JimfsStream outputStream = (JimfsStream) Files.newOutputStream(Paths.get(file), openOptions.toArray(new OpenOption[0]));
                    fileDescriptors.put(fd, outputStream);
                    break;
                default:
                    return -1;
            }
            return fd;
        } catch (IOException e) {
            return -1;
        }
    }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Fcntl_isOpen")
    public static boolean isOpen(IsolateThread thread, int fildes) {
        return fileDescriptors.containsKey(fildes);
    }
}
