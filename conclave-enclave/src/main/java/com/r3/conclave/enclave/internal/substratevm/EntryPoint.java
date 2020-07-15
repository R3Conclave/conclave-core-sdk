package com.r3.conclave.enclave.internal.substratevm;

import com.r3.conclave.enclave.internal.NativeEnclaveEnvironment;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

public class EntryPoint {

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_entryPoint")
    public static void entryPoint(IsolateThread thread, CCharPointer input, int len) {
        byte[] byteArray = new byte[len];
        CTypeConversion.asByteBuffer(input, len).get(byteArray);
        NativeEnclaveEnvironment.enclaveEntry(byteArray);
    }
}