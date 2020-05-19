package com.r3.conclave.core.common

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * A [Handler] that's a counterpart of [ChannelInitiatingHandler]. It creates channels dynamically when an
 * [ChannelDiscriminator.OPEN] message is received, using [createHandler] to create the [Handler] of the underlying
 * channel.
 */
abstract class ChannelHandlingHandler : Handler<ChannelHandlingHandler.Connection> {
    /**
     * Create a [Handler] that will handle a specific channel's input.
     */
    abstract fun createHandler(): Handler<*>

    private val channelCounter = AtomicInteger(0)
    private val muxingHandler = MuxingHandler()

    final override fun onReceive(connection: Connection, input: ByteBuffer) {
        when (val discriminator = input.get()) {
            ChannelDiscriminator.OPEN.value -> {
                val requestId = input.getInt()
                val muxId = channelCounter.getAndIncrement()
                if (muxId == CREATE_CHANNEL_ID) {
                    throw IllegalStateException("Too many channels requested.")
                }
                connection.muxConnection.addDownstream(muxId, createHandler())
                connection.newChannelAck(requestId, muxId)
                require(!input.hasRemaining()) {
                    "Found ${input.remaining()} extra byte(s) after opening new channel."
                }
            }
            ChannelDiscriminator.CLOSE.value -> {
                val muxId = input.getInt()
                connection.muxConnection.removeDownstream(muxId)
            }
            ChannelDiscriminator.PAYLOAD.value -> {
                muxingHandler.onReceive(connection.muxConnection, input)
            }
            else -> throw IllegalStateException("Unknown ChannelDiscriminator '$discriminator'")
        }
    }

    final override fun connect(upstream: Sender): Connection {
        return Connection(upstream, muxingHandler.connect(upstream))
    }

    class Connection(private val upstream: Sender, val muxConnection: MuxingHandler.Connection) {
        fun getChannelIds(): List<MuxId> {
            return muxConnection.getMuxIds()
        }

        internal fun newChannelAck(requestId: MuxId, muxId: MuxId) {
            upstream.send(3 * MuxId.SIZE_BYTES, Consumer { buffer ->
                buffer.putInt(CREATE_CHANNEL_ID)
                buffer.putInt(requestId)
                buffer.putInt(muxId)
            })
        }
    }
}