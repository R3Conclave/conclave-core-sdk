package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.nullableRead
import com.r3.conclave.utilities.internal.nullableWrite
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * This class handles serialisation and de-serialisation of messages passed between the
 * [com.r3.conclave.host.internal.StreamHostEnclaveInterface] and
 * [com.r3.conclave.enclave.internal.StreamEnclaveHostInterface] classes.
 */
class StreamCallInterfaceMessage(
        val hostThreadID: Long,
        val callTypeID: Byte,
        val messageTypeID: Byte,
        val payload: ByteArray?
) {
    companion object {
        fun readFromStream(inputStream: InputStream): StreamCallInterfaceMessage {
            val dis = DataInputStream(inputStream)
            val hostThreadID = dis.readLong()
            val callTypeID = dis.readByte()
            val messageTypeID = dis.readByte()
            val payload = dis.nullableRead { readIntLengthPrefixBytes() }
            return StreamCallInterfaceMessage(hostThreadID, callTypeID, messageTypeID, payload)
        }
    }

    fun writeToStream(outputStream: OutputStream) {
        val dos = DataOutputStream(outputStream)
        dos.writeLong(hostThreadID)
        dos.writeByte(callTypeID.toInt())
        dos.writeByte(messageTypeID.toInt())
        dos.nullableWrite(payload) { writeIntLengthPrefixBytes(it) }
    }
}
