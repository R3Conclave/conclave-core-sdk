package com.r3.conclave.common.internal

/**
 * Enumeration of all possible enclave -> host calls
 */
enum class HostCallType(val hasReturnValue: Boolean) {
    GET_SIGNED_QUOTE(true),
    GET_QUOTING_ENCLAVE_INFO(true);

    fun toShort(): Short {
        check(VALUES.size < Short.MAX_VALUE)
        return ordinal.toShort()
    }

    companion object {
        private val VALUES = HostCallType.values()

        fun fromShort(i: Short): HostCallType {
            require(i < VALUES.size) { "$i does not correspond to a valid host call type." }
            return VALUES[i.toInt()]
        }
    }
}
