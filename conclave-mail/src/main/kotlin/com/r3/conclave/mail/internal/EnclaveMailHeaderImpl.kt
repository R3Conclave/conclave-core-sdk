package com.r3.conclave.mail.internal

import com.r3.conclave.mail.EnclaveMailHeader
import java.io.DataOutputStream
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
        override val envelope: ByteArray?,
        val keyDerivation: ByteArray?
) : EnclaveMailHeader {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnclaveMailHeaderImpl) return false

        if (sequenceNumber != other.sequenceNumber) return false
        if (topic != other.topic) return false
        if (!Arrays.equals(envelope, other.envelope)) return false
        if (!Arrays.equals(keyDerivation, other.keyDerivation)) return false

        return true
    }

    fun encodeTo(out: DataOutputStream) {
        out.writeLong(sequenceNumber)
        out.writeUTF(topic)
        out.writeLengthPrefixBytes(envelope)
        out.writeLengthPrefixBytes(keyDerivation)
    }

    private fun DataOutputStream.writeLengthPrefixBytes(bytes: ByteArray?) {
        if (bytes != null) {
            writeShort(bytes.size)
            write(bytes)
        } else {
            writeShort(0)
        }
    }

    fun encodedSize(): Int = 8 + 2 + topic.length + envelope.encodedSize() + keyDerivation.encodedSize()

    private fun ByteArray?.encodedSize() = if (this != null) 2 + size else 2

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + envelope.contentHashCode()
        result = 31 * result + keyDerivation.contentHashCode()
        return result
    }
}
