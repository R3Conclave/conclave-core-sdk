package com.r3.conclave.enclave.internal.substratevm;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

@CContext(PosixDirectives.class)
public class Stat {

    @CStruct(value = "stat64", addStructKeyword = true)
    public interface Stat64 extends PointerBase {
        @CFieldAddress
        Timespec st_mtim();
    }

    // https://refspecs.linuxbase.org/LSB_5.0.0/LSB-Core-generic/LSB-Core-generic/baselib---xstat64.html
    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_Stat_xstat64")
    public static int xstat64(@SuppressWarnings("unused") IsolateThread thread,
                              @SuppressWarnings("unused") int ver,
                              CCharPointer buf, Stat64 stat, CIntPointer errno) {
        try {
            final String filePath = CTypeConversion.toJavaString(buf);
            Path path = Paths.get(filePath);
            if (filePath.isEmpty() || !Files.exists(path)) {
                errno.write(ErrnoBase.ENOENT);
                return -1;
            }
            final BasicFileAttributes fileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
            final Instant instant = fileAttributes.lastModifiedTime().toInstant();
            stat.st_mtim().setTvSec(instant.getEpochSecond());
            stat.st_mtim().setTvNsec(instant.getNano());
            return 0;
        } catch (IOException e) {
            errno.write(ErrnoBase.EIO);
        } catch (SecurityException e) {
            errno.write(ErrnoBase.EACCES);
        }
        return -1;
    }
}
