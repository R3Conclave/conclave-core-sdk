package com.r3.conclave.testing

import com.r3.conclave.common.internal.handler.BytesHandler
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.*

/**
 * A utility ByteArray-based enclave host that records all received into a list.
 */
class BytesRecordingHandler : BytesHandler() , Closeable {
    private val calls = LinkedList<ByteBuffer>()
    override fun onReceive(connection: Connection, input: ByteBuffer) {
        calls.addLast(input.duplicate())
    }

    fun hasNext(): Boolean = calls.isNotEmpty()

    val nextCall: ByteBuffer get() = calls.pollLast()
    val size: Int get() = calls.size

    fun clear() {
        calls.clear()
    }

    override fun close() {
        clear()
    }
}