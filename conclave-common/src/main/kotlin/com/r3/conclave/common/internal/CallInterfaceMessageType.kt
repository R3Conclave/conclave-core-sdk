package com.r3.conclave.common.internal

/**
 * This forms part of the messaging protocol for enclave and host calls in native mode.
 * Messages (ECalls & OCalls) may represent calls, returns or exceptions.
 */
enum class CallInterfaceMessageType {
    CALL,
    RETURN,
    EXCEPTION;

    fun toByte(): Byte = ordinal.toByte()

    companion object {
        private val VALUES = CallInterfaceMessageType.values()

        @JvmStatic
        fun fromByte(byte: Byte): CallInterfaceMessageType {
            return VALUES[byte.toInt()]
        }
    }
}
