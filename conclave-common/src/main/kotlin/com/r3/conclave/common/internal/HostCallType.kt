package com.r3.conclave.common.internal

/**
 * Enumeration of all possible enclave -> host calls
 */
enum class HostCallType {
    GET_SIGNED_QUOTE,
    GET_QUOTING_ENCLAVE_INFO,
    GET_ATTESTATION,
    SET_ENCLAVE_INFO,
    CALL_MESSAGE_HANDLER;

    fun toByte(): Byte {
        check(VALUES.size < Byte.MAX_VALUE)
        return ordinal.toByte()
    }

    companion object {
        private val VALUES = HostCallType.values()

        fun fromByte(i: Byte): HostCallType {
            require(i < VALUES.size) { "$i does not correspond to a valid host call type." }
            return VALUES[i.toInt()]
        }
    }
}
