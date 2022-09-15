package com.r3.conclave.common.internal

/**
 * Enumeration of all possible host -> enclave calls
 */
enum class EnclaveCallType(val hasParameters: Boolean, val hasReturnValue: Boolean) {
    INITIALIZE_ENCLAVE(true, false);

    fun toShort(): Short {
        check(VALUES.size < Short.MAX_VALUE)
        return ordinal.toShort()
    }

    companion object {
        private val VALUES = EnclaveCallType.values()

        fun fromShort(i: Short): EnclaveCallType {
            require(i < VALUES.size) { "$i does not correspond to a valid enclave call type." }
            return VALUES[i.toInt()]
        }
    }
}
