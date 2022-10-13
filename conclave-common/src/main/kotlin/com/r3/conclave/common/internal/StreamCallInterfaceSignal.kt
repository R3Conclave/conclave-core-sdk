package com.r3.conclave.common.internal

/**
 * This enum represents signals sent between stream call interfaces.
 * See [com.r3.conclave.enclave.internal.StreamEnclaveHostInterface] and
 * [com.r3.conclave.host.internal.StreamHostEnclaveInterface].
 */
enum class StreamCallInterfaceSignal {
    MESSAGE,    // Signals the arrival of a message.
    STOP;       // Signals that no further messages will arrive.

    fun toByte(): Byte = ordinal.toByte()

    companion object {
        private val VALUES = values()

        fun fromByte(byte: Byte): StreamCallInterfaceSignal {
            return VALUES[byte.toInt()]
        }
    }
}
