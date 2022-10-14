package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.nullableRead
import com.r3.conclave.utilities.internal.nullableWrite
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

enum class StreamCallInterfaceMessageType {
    CALL,           // A message initiating a call
    RETURN,         // A return value from an enclave/host call
    EXCEPTION,      // An exception occurred on the other side while handling the call.
    STOP;           // No more messages, used to signal that workers should stop.

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
    companion object {
        val STOP_MESSAGE = StreamCallInterfaceMessage(0, StreamCallInterfaceMessageType.STOP, 0, null)

        fun readFromStream(inputStream: InputStream): StreamCallInterfaceMessage {
            val dis = DataInputStream(inputStream)

            val hostThreadID = dis.readLong()
            val messageType = StreamCallInterfaceMessageType.fromByte(dis.readByte())
            val callTypeID = dis.readByte()
            val payload = dis.nullableRead { readIntLengthPrefixBytes() }

            return StreamCallInterfaceMessage(hostThreadID, messageType, callTypeID, payload)
        }
    }

    fun writeToStream(outputStream: OutputStream) {
        val dos = DataOutputStream(outputStream)

        dos.writeLong(hostThreadID)
        dos.writeByte(messageType.ordinal)
        dos.writeByte(callTypeID.toInt())
        dos.nullableWrite(payload) { writeIntLengthPrefixBytes(it) }
    }
}
