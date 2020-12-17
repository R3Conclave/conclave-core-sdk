package com.r3.conclave.filesystem.jimfs;

import java.io.Closeable;
import java.io.IOException;

public interface JimfsStream extends Closeable {
    int available() throws IOException;

    long position();

    void position(long newPosition) throws IllegalArgumentException;
}
