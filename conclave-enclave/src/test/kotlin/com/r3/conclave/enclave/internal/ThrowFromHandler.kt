package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.handler.ErrorHandler
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.common.internal.handler.ThrowingErrorHandler
import java.nio.ByteBuffer

/**
 * A [Handler] that throws errors if they happen in downstream handlers.
 */
class ThrowFromHandler<CONNECTION>(private val handler: Handler<CONNECTION>) :
    Handler<ThrowFromHandler<CONNECTION>.Connection> {
    private val errorHandler = ThrowingErrorHandler()

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        errorHandler.onReceive(connection.errorConnection, input)
    }

    override fun connect(upstream: Sender): Connection {
        val errorConnected = errorHandler.connect(upstream)
        val downstreamConnected = errorConnected.setDownstream(handler)
        return Connection(errorConnected, downstreamConnected)
    }

    inner class Connection(
        val errorConnection: ErrorHandler.Connection,
        val downstream: CONNECTION
    )
}
