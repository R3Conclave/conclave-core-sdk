package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.*
import java.nio.ByteBuffer

enum class StreamCallInterfaceMessageType {
    CALL,           // A message initiating a call
    RETURN,         // A return value from an enclave/host call
    EXCEPTION,      // An exception occurred on the other side while handling the call.
    STOP;           // No more messages, used to signal that workers should stop.

    fun toByte() = ordinal.toByte()

    companion object {
        private val VALUES = values()

        fun fromByte(b: Byte) = VALUES[b.toInt()]
    }
}

/**
 * This class handles serialisation and de-serialisation of messages passed between the
 * [com.r3.conclave.host.internal.StreamHostEnclaveInterface] and
 * [com.r3.conclave.enclave.internal.StreamEnclaveHostInterface] classes.
 */
class StreamCallInterfaceMessage(
        val hostThreadID: Long,
        val messageType: StreamCallInterfaceMessageType,
        val callTypeID: Byte,
        val payload: ByteArray?
) {
    fun size() = 8 + 1 + 1 + nullableSize(payload) { it.intLengthPrefixSize }

    companion object {
        val STOP_MESSAGE = StreamCallInterfaceMessage(0, StreamCallInterfaceMessageType.STOP, 0, null)

        fun fromByteArray(bytes: ByteArray): StreamCallInterfaceMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val hostThreadID = buffer.long
            val messageType = StreamCallInterfaceMessageType.fromByte(buffer.get())
            val callTypeID = buffer.get()
            val payload = buffer.getNullable { getIntLengthPrefixBytes() }
            return StreamCallInterfaceMessage(hostThreadID, messageType, callTypeID, payload)
        }
    }

    fun toByteArray(): ByteArray {
        return ByteBuffer.allocate(size()).apply {
            putLong(hostThreadID)
            put(messageType.toByte())
            put(callTypeID)
            putNullable(payload) { putIntLengthPrefixBytes(it) }
            rewind()
        }.array()
    }
}
