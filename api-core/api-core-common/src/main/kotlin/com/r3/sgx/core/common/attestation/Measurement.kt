package com.r3.sgx.core.common.attestation

import com.r3.sgx.core.common.SgxMeasurement
import java.nio.ByteBuffer
import java.util.*
import javax.xml.bind.DatatypeConverter

/**
 * Represents an SGX enclave measurement
 */
class Measurement(private val data: ByteArray) {
    init {
        require(data.size == SgxMeasurement.size)
    }

    constructor(hex: String): this(DatatypeConverter.parseHexBinary(hex))

    override fun equals(other: Any?): Boolean {
        return if ( other is Measurement) {
            Arrays.equals(data, other.data)
        } else false
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(data)
    }

    override fun toString(): String = DatatypeConverter.printHexBinary(data)

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
        fun read(bytes: ByteBuffer): Measurement {
            val data = ByteArray(SgxMeasurement.size)
            bytes.get(data)
            return Measurement(data)
        }
    }
}
