package com.r3.conclave.enclave.internal.substratevm;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

@CContext(PosixDirectives.class)
@CStruct(value = "timespec", addStructKeyword = true)
public interface Timespec extends PointerBase {
    @CField
    long tv_sec();

    @CField("tv_sec")
    void setTvSec(long tvSec);

    @CField
    long tv_nsec();

    @CField("tv_nsec")
    void setTvNsec(long tvNsec);
}
