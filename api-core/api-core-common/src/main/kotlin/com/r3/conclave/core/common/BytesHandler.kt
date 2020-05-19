package com.r3.conclave.core.common

import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * A [Handler] that handles [ByteBuffer]s directly. This is a convenience API with incurring some performance cost as
 * sending will do an extra copy because of framing.
 */
abstract class BytesHandler : Handler<BytesHandler.Connection> {
    final override fun connect(upstream: Sender): Connection {
        return Connection(upstream)
    }
    class Connection(val upstream: Sender) {
        fun send(bytes: ByteBuffer) {
            upstream.send(bytes.remaining(), Consumer { buffer ->
                buffer.put(bytes)
            })
        }
    }
}
