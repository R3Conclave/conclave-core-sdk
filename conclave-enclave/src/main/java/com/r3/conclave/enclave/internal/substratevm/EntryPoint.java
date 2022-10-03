package com.r3.conclave.enclave.internal.substratevm;

import com.r3.conclave.common.internal.CallInterfaceMessageType;
import com.r3.conclave.enclave.internal.NativeEnclaveEnvironment;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("unused")
public class EntryPoint {

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_entryPoint")
    public static void entryPoint(IsolateThread thread, byte callTypeID, byte messageTypeID, CCharPointer data, int dataLengthBytes) {
        // As mentioned in the JavaDocs, the default byte order for a new ByteBuffer is _always_ big endian.
        // CTypeConversion.asByteBuffer however uses native byte order. So to make sure we don't break code that
        // assumes the default, we switch back to big endian.
        ByteBuffer parameterBuffer = CTypeConversion.asByteBuffer(data, dataLengthBytes).order(ByteOrder.BIG_ENDIAN).asReadOnlyBuffer();
        NativeEnclaveEnvironment.enclaveEntry(callTypeID, CallInterfaceMessageType.Companion.fromByte(messageTypeID), parameterBuffer);
    }

    @CEntryPoint(name = "Java_com_r3_conclave_enclave_internal_substratevm_EntryPoint_internalError")
    public static void internalError(IsolateThread thread, CCharPointer input, int len) {
        byte[] byteArray = new byte[len];
        CTypeConversion.asByteBuffer(input, len).get(byteArray);
        throw new InternalError(new String(byteArray, StandardCharsets.UTF_8));
    }
}
