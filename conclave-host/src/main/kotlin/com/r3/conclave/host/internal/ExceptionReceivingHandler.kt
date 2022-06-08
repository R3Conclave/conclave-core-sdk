package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.ThrowableSerialisation
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import com.r3.conclave.common.internal.handler.Sender
import com.r3.conclave.utilities.internal.getBytes
import java.nio.ByteBuffer

/**
 * A [Handler] that handle exceptions received from an `ExceptionSendingHandler`.
 */
class ExceptionReceivingHandler : Handler<ExceptionReceivingHandler.Connection> {
    override fun onReceive(connection: Connection, input: ByteBuffer) {
        when (input.get()) {
            ThrowableSerialisation.Discriminator.ERROR.value -> {
                throw parseException(input)
            }
            ThrowableSerialisation.Discriminator.NO_ERROR.value -> {
                val downstream = connection.downstream ?: throw IllegalStateException("Downstream not set")
                downstream.onReceive(input)
            }
            else -> throw IllegalArgumentException("Unrecognized error discriminator")
        }
    }

    override fun connect(upstream: Sender): Connection {
        return Connection(upstream)
    }

    private fun parseException(input: ByteBuffer): Throwable {
        return try {
            ThrowableSerialisation.deserialise(input)
        } catch (throwable: Throwable) {
            input.mark()
            val size = Integer.min(input.remaining(), 64)
            val bytes = input.getBytes(size)
            input.reset()
            IllegalArgumentException("Cannot parse exception bytes starting with ${bytes.toList()}", throwable)
        }
    }

    class Connection(private val upstream: Sender) {
        private var _downstream: HandlerConnected<*>? = null
        val downstream: HandlerConnected<*>? get() = _downstream

        @Synchronized
        fun <CONNECTION> setDownstream(downstream: Handler<CONNECTION>): CONNECTION {
            if (this._downstream != null) {
                throw IllegalArgumentException("Can only have a single downstream")
            } else {
                val connection = downstream.connect(upstream)
                this._downstream = HandlerConnected(downstream, connection)
                return connection
            }
        }
    }
}
