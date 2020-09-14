package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.getIntLengthPrefixBytes
import com.r3.conclave.utilities.internal.getRemainingBytes
import com.r3.conclave.utilities.internal.putIntLengthPrefixBytes
import java.nio.ByteBuffer
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Messages sent from enclave to host, to serialise the postMail and acknowledgeMail calls.
 */
sealed class MailCommand {
    abstract val serialisedSize: Int

    /**
     * Serialise this mail command into the remaining space of this buffer. This method assumes there are exactly
     * [serialisedSize] bytes remaining, i.e. this command is being serialised at the end of the buffer.
     */
    abstract fun putTo(buffer: ByteBuffer)

    data class Acknowledge(val id: Long) : MailCommand() {
        override val serialisedSize: Int get() = 1 + Long.SIZE_BYTES

        override fun putTo(buffer: ByteBuffer) {
            buffer.put(0)
            buffer.putLong(id)
        }
    }

    data class Post(val routingHint: String?, val bytes: ByteArray) : MailCommand() {
        private val routingHintBytes by lazy(NONE) { routingHint?.toByteArray() }

        override val serialisedSize: Int
            get() = 1 + Int.SIZE_BYTES + (routingHintBytes?.size ?: 0) + bytes.size

        override fun putTo(buffer: ByteBuffer) {
            buffer.put(1)
            if (routingHintBytes == null) {
                buffer.putInt(0)
            } else {
                buffer.putIntLengthPrefixBytes(routingHintBytes!!)
            }
            buffer.put(bytes)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Post) return false
            if (!bytes.contentEquals(other.bytes)) return false
            return true
        }

        override fun hashCode() = bytes.contentHashCode()
    }

    companion object {
        /**
         * Deserialise a mail command from the remaining bytes of the given buffer.
         */
        fun deserialise(buffer: ByteBuffer): MailCommand {
            return when (buffer.get().toInt()) {
                0 -> Acknowledge(buffer.getLong())
                1 -> Post(
                        // routingHint can be null/missing.
                        String(buffer.getIntLengthPrefixBytes()).takeIf { it.isNotBlank() },
                        // rest of the body to deliver (should be encrypted).
                        buffer.getRemainingBytes()
                )
                else -> throw IllegalStateException("Unknown type byte")
            }
        }
    }
}
