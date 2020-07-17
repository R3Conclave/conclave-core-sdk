package com.r3.conclave.common.internal

import com.r3.conclave.mail.EnclaveMailId
import com.r3.conclave.utilities.internal.readIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.writeIntLengthPrefixBytes
import java.io.DataInputStream
import java.io.DataOutputStream


// TODO: Change this to serialize into ByteBuffers instead of streams, to work better with the handler framework.

/**
 * Messages sent from enclave to host, to serialise the postMail and acknowledgeMail calls.
 */
sealed class MailCommand {
    abstract fun writeTo(stream: DataOutputStream)

    data class Acknowledge(val id: EnclaveMailId) : MailCommand() {
        override fun writeTo(stream: DataOutputStream) {
            stream.write(0)
            stream.writeLong(id)
        }
    }

    data class Post(val routingHint: String?, val bytes: ByteArray) : MailCommand() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Post) return false
            if (!bytes.contentEquals(other.bytes)) return false
            return true
        }

        override fun hashCode() = bytes.contentHashCode()

        override fun writeTo(stream: DataOutputStream) {
            stream.write(1)
            if (routingHint == null)
                stream.writeInt(0)
            else
                stream.writeIntLengthPrefixBytes(routingHint.toByteArray())
            stream.writeIntLengthPrefixBytes(bytes)
        }
    }

    companion object {
        fun deserialise(stream: DataInputStream): MailCommand {
            return when (stream.read()) {
                -1 -> throw IllegalStateException("End of stream")
                0 -> Acknowledge(stream.readLong())
                1 -> {
                    Post(
                            // routingHint can be null/missing.
                            String(stream.readIntLengthPrefixBytes()).takeIf { it.isNotBlank() },
                            // rest of the body to deliver (should be encrypted).
                            stream.readIntLengthPrefixBytes()
                    )
                }
                else -> throw IllegalStateException("Unknown type byte")
            }
        }
    }
}