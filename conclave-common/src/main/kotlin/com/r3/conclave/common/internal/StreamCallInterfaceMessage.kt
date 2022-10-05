package com.r3.conclave.common.internal

import com.r3.conclave.mail.internal.readInt
import com.r3.conclave.mail.internal.writeInt
import com.r3.conclave.utilities.internal.*
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class StreamCallInterfaceMessage(
        val hostThreadID: Long,
        val callTypeID: Byte,
        val messageTypeID: Byte,
        val payload: ByteArray?
) {
    private val headerSize = 8 + 1 + 1
    private val payloadSize get() = (payload?.size ?: 0) + 1

    fun size() = headerSize + payloadSize

    companion object {
        fun fromBytes(bytes: ByteArray): StreamCallInterfaceMessage {
            val buffer = ByteBuffer.wrap(bytes)

            val hostThreadID = buffer.long
            val callTypeID = buffer.get()
            val messageTypeID = buffer.get()
            val messageBytes = buffer.getNullable { getRemainingBytes() }

            return StreamCallInterfaceMessage(hostThreadID, callTypeID, messageTypeID, messageBytes)
        }

        fun readFromStream(inputStream: InputStream): StreamCallInterfaceMessage {
            val bytesToRead = inputStream.readInt()
            val bytes = ByteArray(bytesToRead)
            val bytesRead = inputStream.read(bytes)

            check(bytesRead == bytesToRead) { "Unexpected end of stream." }

            return fromBytes(bytes)
        }
    }

    fun toBytes(): ByteArray {
        return ByteBuffer.allocate(size()).apply {
            putLong(hostThreadID)
            put(callTypeID)
            put(messageTypeID)
            putNullable(payload) { put(payload) }
        }.array()
    }

    fun writeToStream(outputStream: OutputStream) {
        val array = this.toBytes()
        outputStream.writeInt(array.size)
        outputStream.write(array)
    }
}
