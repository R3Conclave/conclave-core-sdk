@file:JvmName("Channels")
package com.r3.conclave.common.internal.handler

import com.r3.conclave.common.internal.handler.ChannelInitiatingHandler.Connection
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

private typealias HandlerFuture<CONNECTION> = Pair<Handler<CONNECTION>, CompletableFuture<Channel<CONNECTION>>>

const val CREATE_CHANNEL_ID: MuxId = -1

data class Channel<CONNECTION>(val id: MuxId, val connection: CONNECTION)

/**
 * A [Handler] capable of creating new channels dynamically with the other side, which should implement
 * [ChannelHandlingHandler]. New channels are created by calling [Connection.addDownstream].
 */
class ChannelInitiatingHandler : Handler<ChannelInitiatingHandler.Connection> {
    private val muxingHandler = MuxingHandler()

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        val muxId = input.getInt(input.position())
        if (muxId == CREATE_CHANNEL_ID) {
            input.getInt() // Discard the "create channel" cookie
            val requestId = input.getInt()
            val channelId = input.getInt()
            connection.complete(requestId, channelId)
            require(!input.hasRemaining()) {
                "Found ${input.remaining()} extra byte(s) after opening new channel."
            }
        } else {
            muxingHandler.onReceive(connection.muxConnection, input)
        }
    }

    override fun connect(upstream: Sender): Connection {
        return Connection(muxingHandler.connect(PayloadFramingSender(upstream)), upstream)
    }

    private class PayloadFramingSender(val upstream: Sender) : Sender {
        override fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>) {
            serializers.add(Consumer { buffer ->
                buffer.put(ChannelDiscriminator.PAYLOAD.value)
            })
            upstream.send(needBytes + Byte.SIZE_BYTES, serializers)
        }
    }

    class Connection(val muxConnection: MuxingHandler.Connection, val upstream: Sender) {
        private val requestMap: MutableMap<Int, HandlerFuture<in Any>> = ConcurrentHashMap()
        private val requestCounter = AtomicInteger(0)

        /**
         * Create a new channel.
         * @param handler the [Handler] handling a specific channel's input.
         */
        fun <CONNECTION> addDownstream(handler: Handler<CONNECTION>): CompletableFuture<Channel<CONNECTION>> {
            val future = CompletableFuture<Channel<CONNECTION>>()
            val requestId = requestCounter.getAndIncrement()
            @Suppress("unchecked_cast")
            requestMap[requestId] = HandlerFuture(handler, future) as HandlerFuture<in Any>

            upstream.send(Byte.SIZE_BYTES + Int.SIZE_BYTES, Consumer { buffer ->
                buffer.put(ChannelDiscriminator.OPEN.value)
                buffer.putInt(requestId)
            })
            return future
        }

        internal fun complete(requestId: Int, channelId: MuxId) {
            val (handler, future) = requestMap.remove(requestId)
                    ?: throw IllegalStateException("Invalid channel connection request=$requestId")
            val connection = muxConnection.addDownstream(channelId, handler)
            future.complete(Channel(channelId, connection))
        }

        fun removeDownstream(channelId: MuxId) {
            upstream.send(Byte.SIZE_BYTES + Int.SIZE_BYTES, Consumer { buffer ->
                buffer.put(ChannelDiscriminator.CLOSE.value)
                buffer.putInt(channelId)
            })
            muxConnection.removeDownstream(channelId)
        }

        fun getChannelIds(): List<MuxId> {
            return muxConnection.getMuxIds()
        }
    }
}