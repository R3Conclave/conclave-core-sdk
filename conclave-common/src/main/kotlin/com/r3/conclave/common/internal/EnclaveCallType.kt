package com.r3.conclave.common.internal

/**
 * Enumeration of all possible host -> enclave calls
 */
enum class EnclaveCallType {
    INITIALISE_ENCLAVE,
    START_ENCLAVE,
    STOP_ENCLAVE,
    GET_ENCLAVE_INSTANCE_INFO_QUOTE,
    GET_KDS_PERSISTENCE_KEY_SPEC,
    SET_KDS_PERSISTENCE_KEY,
    CALL_MESSAGE_HANDLER;

    fun toByte(): Byte = ordinal.toByte()

    companion object {
        private val VALUES = EnclaveCallType.values()

        @JvmStatic
        fun fromByte(i: Byte): EnclaveCallType {
            require(i < VALUES.size) { "$i does not correspond to a valid enclave call type." }
            return VALUES[i.toInt()]
        }
    }
}
