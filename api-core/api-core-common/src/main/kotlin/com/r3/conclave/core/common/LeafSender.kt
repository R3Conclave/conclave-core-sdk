package com.r3.conclave.core.common

import java.nio.ByteBuffer
import java.nio.Buffer
import java.util.function.Consumer

/**
 * Helper abstract class for leaf senders.
 */
abstract class LeafSender : Sender {
    /**
     * Send the now-serialized [ByteBuffer]
     */
    abstract fun sendSerialized(serializedBuffer: ByteBuffer)

    /**
     * Allocate a [needBytes] sized buffer, then traverse and call [serializers] backwards.
     */
    final override fun send(needBytes: Int, serializers: MutableList<Consumer<ByteBuffer>>) {
        val buffer = ByteBuffer.allocate(needBytes)
        for (serializer in serializers.asReversed()) {
            serializer.accept(buffer)
        }
        assert(buffer.position() == needBytes)
        // Cast to a Buffer as the JDK8 ByteBuffer.flip() function specification
        // changed in JDK9+
        (buffer as Buffer).flip()
        sendSerialized(buffer)
    }
}
