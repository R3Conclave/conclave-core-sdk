package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.*
import java.nio.ByteBuffer

enum class SocketCallInterfaceMessageType {
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
 * [com.r3.conclave.host.internal.SocketHostEnclaveInterface] and
 * [com.r3.conclave.enclave.internal.SocketEnclaveHostInterface] classes.
 */
class SocketCallInterfaceMessage(
        val messageType: SocketCallInterfaceMessageType,
        val callTypeID: Byte,
        val payload: ByteArray?
) {
    fun size() = 1 + 1 + nullableSize(payload) { it.intLengthPrefixSize }

    companion object {
        val STOP_MESSAGE = SocketCallInterfaceMessage(SocketCallInterfaceMessageType.STOP, 0, null)

        fun fromByteArray(bytes: ByteArray): SocketCallInterfaceMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val messageType = SocketCallInterfaceMessageType.fromByte(buffer.get())
            val callTypeID = buffer.get()
            val payload = buffer.getNullable { getIntLengthPrefixBytes() }
            return SocketCallInterfaceMessage(messageType, callTypeID, payload)
        }
    }

    fun toByteArray(): ByteArray {
        return ByteBuffer.allocate(size()).apply {
            put(messageType.toByte())
            put(callTypeID)
            putNullable(payload) { putIntLengthPrefixBytes(it) }
            rewind()
        }.array()
    }
}
