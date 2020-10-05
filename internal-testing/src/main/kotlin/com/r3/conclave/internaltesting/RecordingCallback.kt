package com.r3.conclave.internaltesting

import java.util.function.Function

class RecordingCallback : Function<ByteArray, ByteArray?> {
    val calls = ArrayList<ByteArray>()

    override fun apply(bytes: ByteArray): ByteArray? {
        calls += bytes
        return null
    }
}
