package com.r3.conclave.common.internal

enum class StreamCallInterfaceThreadCommand {
    MESSAGE,
    STOP;

    fun toByte(): Byte = ordinal.toByte()

    companion object {
        private val VALUES = values()

        fun fromByte(byte: Byte): StreamCallInterfaceThreadCommand {
            return VALUES[byte.toInt()]
        }
    }
}
