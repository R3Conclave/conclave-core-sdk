package com.r3.conclave.common.internal

/**
 * This forms part of the messaging protocol for enclave and host calls in native mode.
 * Messages (ecalls & ocalls) may represent calls, returns or exceptions.
 */
enum class NativeMessageType {
    CALL,
    RETURN,
    EXCEPTION;

    fun toByte(): Byte {
        return ordinal.toByte()
    }

    companion object {
        private val VALUES = NativeMessageType.values()

        fun fromByte(byte: Byte): NativeMessageType {
            return VALUES[byte.toInt()]
        }
    }
}
