package com.r3.conclave.core.common

import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * This is the sending side of [Handler]. Implementations serialize and send sequences of bytes.
 */
interface Sender {
    /**
     * Send some bytes. The bottom sender will traverse [serializers] backwards so that the bottom serializer can
     * write their frame first, then the second-to-last, and so on.
     *
     * Let's say we have two parts of the final message, a message body B and a header H that indicates which handler
     * the message should be handled by on the other side.
     *
     * We'll have two layers of senders (and a bottom one that actually sends the full thing), one that serializes B, and
     * another that serializes H.
     * To reduce copying we want to use a single ByteBuffer of size size(B) + size(H). Hence the inversion of the API:
     *
     * Instead of doing
     * ```
     * fun serialize(body: B): ByteBuffer
     * ```
     * we do
     * ```
     * fun serialize(bytesNeeded: Int, body: B): Consumer<ByteBuffer>
     * ```
     * That is, we have two stages of serialization, one that adds up the bytesNeeded of each part as the calls chain
     * through senders, and at the bottom we'll allocate the ByteBuffer (now we know the full size) and call the relevant
     * serializers to fill it in.
     *
     * There's another complication, which is the nesting of the serializer closures: this doubles the execution stack
     * size, i.e. we'd have
     * ```
     * BodySender.send
     * HeaderSender.send
     * <bottom>
     * HeaderSenderSerializer
     * BodySenderSerializer
     * ```
     *
     * To reduce this we collect the closures in a list instead and traverse at the bottom. Note also how the order of
     * filling in the final buffer is the reverse of the call chain - this is because we want the bottom-layer
     * information (the Header) to appear at the beginning of the message.
     *
     * @param needBytes indicates that the appended serializer will use this many bytes.
     * @param serializers a mutable list to add your serializer to.
     */
    fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>)

    /**
     * A convenience top-level send.
     *
     * @param needBytes indicates that the passed in serializer will use this many bytes.
     * @param serializer the serializer of the top-level message.
     */
    @JvmDefault
    fun send(needBytes: Int, serializer: Consumer<ByteBuffer>) {
        val serializers = ArrayList<Consumer<ByteBuffer>>()
        serializers.add(serializer)
        send(needBytes, serializers)
    }
}
