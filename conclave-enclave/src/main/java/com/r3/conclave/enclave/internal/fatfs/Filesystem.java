package com.r3.conclave.enclave.internal.fatfs;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;

public class Filesystem {
    private final static long SIZE = Long.parseLong(System.getProperty("com.r3.conclave.fatfs.filesystemsize"));

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_fatfs_Filesystem_getSize")
    public static long getSize(IsolateThread thread) {
        return SIZE;
    }
}
