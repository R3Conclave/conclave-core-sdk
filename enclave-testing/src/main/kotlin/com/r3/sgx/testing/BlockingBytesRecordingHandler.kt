package com.r3.sgx.testing

import com.r3.sgx.core.common.BytesHandler
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

class BlockingBytesRecordingHandler : BytesHandler() {
    var received = ArrayBlockingQueue<ByteArray>(16)
    override fun onReceive(connection: BytesHandler.Connection, input: ByteBuffer) {
        received.add(ByteArray(input.remaining()).also { input.get(it) })
    }
}
