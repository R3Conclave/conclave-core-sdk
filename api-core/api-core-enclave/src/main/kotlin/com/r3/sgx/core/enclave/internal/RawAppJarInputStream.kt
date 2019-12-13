package com.r3.sgx.core.enclave.internal

import java.io.InputStream

/**
 * Reads the statically linked enclave jar.
 * Usage:
 *   val appJar = JarInputStream(RawAppJarInputStream())
 */
class RawAppJarInputStream : InputStream() {
    private var jarOffset = 0L

    override fun read(): Int {
        val data = ByteArray(1)
        if (read(data, 0, 1) != 1) {
            return -1
        } else {
            return data[0].toInt()
        }
    }

    override fun read(dest: ByteArray, offset: Int, length: Int): Int {
        val numberOfBytesRead = Native.readAppJarChunk(jarOffset, dest, offset, length)
        if (numberOfBytesRead > 0) {
            jarOffset += numberOfBytesRead
            return numberOfBytesRead
        } else {
            return -1
        }
    }
}