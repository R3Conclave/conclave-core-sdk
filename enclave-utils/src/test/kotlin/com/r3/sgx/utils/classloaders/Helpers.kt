@file:JvmName("Helpers")
package com.r3.sgx.utils.classloaders

import java.io.File
import java.nio.ByteBuffer

fun ByteBuffer.put(file: File): ByteBuffer {
    file.forEachBlock { block, size ->
        put(block, 0, size)
    }
    return this
}

fun File.toByteBuffer(): ByteBuffer {
    return ByteBuffer.allocate(length().toInt()).also { buf ->
        buf.put(this)
        buf.flip()
    }
}
