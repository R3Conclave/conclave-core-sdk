package com.r3.conclave.mail.internal

import com.r3.conclave.mail.EnclaveMailHeader
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.writeData

/**
 * Encoder/decoder for the authenticated data bytes in a mail stream.
 */
data class EnclaveMailHeaderImpl(
        override val sequenceNumber: Long,
        override val topic: String,
        override val from: String?,
        override val envelope: ByteArray?
) : EnclaveMailHeader {
    companion object {
        /**
         * Decodes the format produced by [encode], which is a custom format.
         */
        fun decode(encoded: ByteArray): EnclaveMailHeaderImpl {
            return encoded.deserialise {
                val seqNo = readLong()
                val topic = readUTF()
                val from = readUTF().takeIf { it.isNotEmpty() }
                val envSize = readUnsignedShort()
                val env = if (envSize == 0) {
                    null
                } else {
                    val env = ByteArray(envSize)
                    val read = read(env)
                    check(read == envSize) { "Wanted to read $envSize bytes in header, got $read" }
                    env
                }
                EnclaveMailHeaderImpl(seqNo, topic, from, env)
            }
        }
    }

    /**
     * Encodes the [EnclaveMailHeaderImpl] into a custom binary format.
     */
    val encoded: ByteArray get() {
        return writeData {
            writeLong(sequenceNumber)
            writeUTF(topic)
            if (from != null) {
                writeUTF(from)
            } else {
                writeShort(0)
            }
            if (envelope != null) {
                writeShort(envelope.size)
                write(envelope)
            } else {
                writeShort(0)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnclaveMailHeaderImpl) return false

        if (sequenceNumber != other.sequenceNumber) return false
        if (topic != other.topic) return false
        if (from != other.from) return false
        if (envelope != null) {
            if (other.envelope == null) return false
            if (!envelope.contentEquals(other.envelope)) return false
        } else if (other.envelope != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + (from?.hashCode() ?: 0)
        result = 31 * result + (envelope?.contentHashCode() ?: 0)
        return result
    }
}
