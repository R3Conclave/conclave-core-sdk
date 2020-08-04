package com.r3.conclave.enclave.internal

import java.io.Closeable
import java.nio.ByteBuffer
import java.util.*

/**
 * Records Sealing Key responses.
 */
class GetSealingKeyRecordingHandler : GetSealingKeyHandler(), Closeable {
    private val calls = LinkedList<ByteBuffer>()
    val nextCall: ByteBuffer get() = calls.pollLast()
    val size: Int get() = calls.size

    fun clear() {
        calls.clear()
    }

    fun hasNext(): Boolean = calls.isNotEmpty()

    override fun onReceive(connection: GetSealingKeySender, key: ByteArray) {
        calls.addLast(ByteBuffer.wrap(key.clone()))
    }

    override fun close() {
        clear()
    }
}