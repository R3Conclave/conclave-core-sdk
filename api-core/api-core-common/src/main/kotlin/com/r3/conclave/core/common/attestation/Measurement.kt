package com.r3.conclave.core.common.attestation

import com.r3.conclave.common.internal.SgxMeasurement
import com.r3.conclave.common.internal.getBytes
import com.r3.conclave.common.internal.parseHex
import com.r3.conclave.common.internal.toHexString
import java.nio.ByteBuffer

/**
 * Represents an SGX enclave measurement
 */
// TODO This should be an OpaqueByte
class Measurement(private val data: ByteArray) {
    init {
        require(data.size == SgxMeasurement.size)
    }

    constructor(hex: String): this(parseHex(hex.toUpperCase()))

    override fun equals(other: Any?): Boolean = other is Measurement && data.contentEquals(other.data)

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = data.toHexString()

    val encoded get() = data.copyOf()

    companion object {
        /**
         * Parse measurement from string representation in hex format
         */
        @JvmStatic
        fun of(hex: String) = Measurement(hex)

        /**
         * Read measurement from buffer
         */
        @JvmStatic
        fun read(bytes: ByteBuffer): Measurement = Measurement(bytes.getBytes(SgxMeasurement.size))
    }
}
