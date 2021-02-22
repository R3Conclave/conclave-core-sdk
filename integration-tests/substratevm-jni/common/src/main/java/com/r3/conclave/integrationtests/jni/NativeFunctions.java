package com.r3.conclave.integrationtests.jni;

import com.r3.conclave.enclave.internal.substratevm.Stat;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

/**
 * This class declares the native functions to be tested in the enclave
 */
public class NativeFunctions {
    @CFunction
    public static native int open(CCharPointer path, int oflag);
    @CFunction
    public static native long write(int fd, PointerBase buf, UnsignedWord nbyte);
    @CFunction
    public static native int close(int fildes);

    @CFunction
    public static native PointerBase malloc(UnsignedWord size);
    @CFunction
    public static native void free(PointerBase pointer);

    @CFunction
    public static native int __xstat64(int version, CCharPointer path, Stat.Stat64 buf);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CIntPointer __errno_location();

}
