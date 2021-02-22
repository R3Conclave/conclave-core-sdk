package com.r3.conclave.integrationtests.jni;

import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import java.io.Closeable;

public class CloseablePointer implements Closeable {
    private final PointerBase pointer;

    public static CloseablePointer allocate(int size) {
        PointerBase pointer = NativeFunctions.malloc(WordFactory.unsigned(size));
        if (pointer.isNull()) {
            throw new OutOfMemoryError();
        }
        return new CloseablePointer(pointer);
    }

    public CloseablePointer(PointerBase pointer) {
        this.pointer = pointer;
    }

    public PointerBase getPointer() {
        return pointer;
    }

    @Override
    public void close() {
        NativeFunctions.free(pointer);
    }
}
