package com.r3.conclave.mail.internal

import com.r3.conclave.mail.EnclaveMailHeader
import com.r3.conclave.mail.MutableMail
import com.r3.conclave.utilities.internal.deserialise
import com.r3.conclave.utilities.internal.writeData
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.util.*

/**
 * Encoder/decoder for the authenticated data bytes in a mail stream.
 *
 * [keyDerivation] is an internal header used by the enclave to derive the private key needed for encryption. Currently
 * it only contains the CPUSVN value, but it's variable length to allow future additions.
 */
data class EnclaveMailHeaderImpl(
        override val sequenceNumber: Long,
        override val topic: String,
        override val from: String?,
        override val envelope: ByteArray?,
        val keyDerivation: ByteArray?
) : EnclaveMailHeader {
    companion object {
        /**
         * Decodes the format produced by [EnclaveMailHeaderImpl.encoded], which is a custom format.
         */
        fun decode(encoded: ByteArray): EnclaveMailHeaderImpl {
            try {
                return encoded.deserialise {
                    val seqNo = readLong()
                    val topic = readUTF()
                    val from = readUTF().takeIf { it.isNotEmpty() }
                    val envelope = readLengthPrefixBytes()
                    val keyDerivation = readLengthPrefixBytes()
                    EnclaveMailHeaderImpl(seqNo, topic, from, envelope, keyDerivation)
                }
            } catch (e: EOFException) {
                throw IllegalArgumentException("Truncated Conclave Mail header", e)
            }
        }

        private fun DataInputStream.readLengthPrefixBytes(): ByteArray? {
            val size = readUnsignedShort()
            return if (size == 0) null else ByteArray(size).also(::readFully)
        }

        private fun DataOutputStream.writeLengthPrefixBytes(bytes: ByteArray?) {
            if (bytes != null) {
                writeShort(bytes.size)
                write(bytes)
            } else {
                writeShort(0)
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
            writeLengthPrefixBytes(envelope)
            writeLengthPrefixBytes(keyDerivation)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnclaveMailHeaderImpl) return false

        if (sequenceNumber != other.sequenceNumber) return false
        if (topic != other.topic) return false
        if (from != other.from) return false
        if (!Arrays.equals(envelope, other.envelope)) return false
        if (!Arrays.equals(keyDerivation, other.keyDerivation)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + (from?.hashCode() ?: 0)
        result = 31 * result + envelope.contentHashCode()
        result = 31 * result + keyDerivation.contentHashCode()
        return result
    }
}
