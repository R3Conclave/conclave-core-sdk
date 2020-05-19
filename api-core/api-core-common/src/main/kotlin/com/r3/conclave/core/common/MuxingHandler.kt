package com.r3.conclave.core.common

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

typealias MuxId = Int

/**
 * A [Handler] that muxes sends using a discriminator.
 */
class MuxingHandler : Handler<MuxingHandler.Connection> {

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        val discriminator = input.getInt()
        val downstream = connection.getDownstream(discriminator)
            ?: throw IllegalStateException("Cannot find downstream with id $discriminator")
        downstream.onReceive(input)
    }

    override fun connect(upstream: Sender): Connection {
        return Connection(upstream)
    }

    class Connection(private val upstream: Sender) {
        private val downstreams = ConcurrentHashMap<MuxId, HandlerConnected<*>>()

        fun sender(muxId: MuxId): Sender {
            return MuxingSender(muxId, upstream)
        }

        fun <CONNECTION> addDownstream(muxId: MuxId, handler: Handler<CONNECTION>): CONNECTION {
            val connection = handler.connect(sender(muxId))
            val previous = downstreams.putIfAbsent(muxId, HandlerConnected(handler, connection))
            if (previous != null) {
                throw IllegalArgumentException("There is already a downstream with mux id $muxId")
            }
            return connection
        }

        /**
         * A special case when two requests might race to install handlers for the
         * same [MuxId]. It is the caller's responsibility to ensure that the connection
         * returned is of the correct type.
         */
        fun <CONNECTION> getOrAddDownstream(muxId: MuxId, handler: Handler<CONNECTION>): HandlerConnected<*> {
            return downstreams.computeIfAbsent(muxId) {
                HandlerConnected(handler, handler.connect(sender(muxId)))
            }
        }

        fun getDownstream(muxId: MuxId): HandlerConnected<*>? {
            return downstreams[muxId]
        }

        fun removeDownstream(muxId: MuxId): HandlerConnected<*>? {
            return downstreams.remove(muxId)
        }

        fun getMuxIds(): List<MuxId> {
            return downstreams.keys().toList()
        }
    }

    private class MuxingSender(val muxId: MuxId, private val upstream: Sender) : Sender {
        override fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>) {
            serializers.add(Consumer { buffer ->
                buffer.putInt(muxId)
            })
            upstream.send(needBytes + MuxId.SIZE_BYTES, serializers)
        }
    }
}
