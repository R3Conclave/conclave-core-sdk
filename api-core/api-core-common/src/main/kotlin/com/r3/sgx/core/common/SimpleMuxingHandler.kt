package com.r3.sgx.core.common

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [MuxingHandler] that auto-generates the mux ids.
 */
class SimpleMuxingHandler : Handler<SimpleMuxingHandler.Connection> {
    private val muxingHandler = MuxingHandler()

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        muxingHandler.onReceive(connection.muxConnection, input)
    }

    override fun connect(upstream: Sender): Connection {
        return Connection(muxingHandler.connect(upstream))
    }

    private val muxIdCounter = AtomicInteger(0)
    inner class Connection(
            val muxConnection: MuxingHandler.Connection
    ) {
        fun <CONNECTION> addDownstream(downstream: Handler<CONNECTION>): CONNECTION {
            val muxId = muxIdCounter.getAndIncrement()
            return muxConnection.addDownstream(muxId, downstream)
        }
    }
}