@file:JvmName("Helpers")
package com.r3.sgx.multiplex.enclave

import java.io.File
import java.nio.ByteBuffer
import kotlin.test.fail

fun mandatoryProperty(name: String): String {
    return System.getProperty(name) ?: fail("Property '$name' not set")
}

fun mandatoryIntProperty(name: String): Int {
    return Integer.getInteger(name) ?: fail("Property '$name' not set")
}

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
