package com.r3.conclave.common.internal

import java.util.HashMap

// see Intel's docs for IODs - 1.3.5 Intel® SGX PCK Certificate
// https://download.01.org/intel-sgx/latest/dcap-latest/linux/docs/Intel_SGX_PCK_Certificate_CRL_Spec-1.4.pdf

// ASN1 types described here https://docs.microsoft.com/en-us/windows/win32/seccertenroll/about-octet-string

class SGXExtensionASN1Parser {
    private val _values = HashMap<String, ByteArray>()
    private val _types = HashMap<String, Int>()

    fun value(id: String?): ByteArray {
        return _values[id] as ByteArray
    }

    fun type(id: String?): Int {
        val i = _types[id]
        return i ?: 0
    }

    fun keys(): Set<String> {
        return _values.keys
    }

    fun types(): Set<String> {
        return _types.keys
    }

    // parse top-level octet string
    // do not decode fields that are octet strings
    fun parse(data: ByteArray, length: Int): Boolean {
        require(length >= 2) { String.format("octet_string: too short %d", length) }
        val objectLength = RefToInt()
        var offset = 0
        val tag = data[offset]
        offset++
        require(tag.toInt() == 0x04) { String.format("octet_string: invalid tag 0x%02X", tag) }
        offset += decode_length(data, offset, objectLength)
        offset += decode(data, offset, objectLength.value)
        return offset == length
    }

    private fun decode(data: ByteArray, base: Int, length: Int): Int {
        val objectLength = RefToInt()

        /*
         * KV pairs are encoded as sequences
         */
        var key: String? = null
        var value: ByteArray? = null
        var type = 0
        var offset = 0
        while (offset < length) {
            val tag = data[base + offset]
            offset++
            when (tag) {
                SEQ_TAG -> {
                    offset += decode_length(data, base + offset, objectLength)
                    decode(data, base + offset, objectLength.value)
                    offset += objectLength.value
                    key = null
                    value = null
                    type = 0
                }
                OID_TAG -> {
                    offset += decode_length(data, base + offset, objectLength)
                    key = ObjectIDString(data, base + offset, objectLength.value)
                    offset += objectLength.value
                }
                OCTSTR_TAG, INT_TAG, ENUM_TAG -> {
                    offset += decode_length(data, base + offset, objectLength)
                    value = slice(data, base + offset, objectLength.value)
                    type = tag.toInt()
                    offset += objectLength.value
                }
                else -> throw Exception(String.format("unknown tag 0x%02X", tag))
            }
            if (key != null && value != null) {
                _values[key] = value
                _types[key] = type
            }
        }
        // number of bytes processed
        return offset
    }

    fun intValue(key: String): Int {
        val b = value(key)
        return intValue(b)
    }

    // TODO Remove the need for this.
    private class RefToInt {
        var value = 0
    }

    companion object {
        private const val SEQ_TAG = 0x30.toByte()
        private const val OID_TAG = 0x06.toByte()
        private const val OCTSTR_TAG = 0x04.toByte()
        private const val INT_TAG = 0x02.toByte()
        private const val ENUM_TAG = 0x0A.toByte()

        private fun decode_length(data: ByteArray, base: Int, result: RefToInt): Int {
            var offset = 0
            var len: Int = data[base + offset].toInt() and 0xFF
            offset++
            if (len > 127) {
                val cnt = len and 0x7F
                len = 0
                for (i in 0 until cnt) {
                    len *= 256
                    len += data[base + offset].toInt() and 0xFF
                    offset++
                }
            }
            result.value = len
            return offset
        }

        private fun slice(data: ByteArray, offset: Int, length: Int): ByteArray {
            return data.copyOfRange(offset, offset + length)
        }

        private fun ObjectIDString(data: ByteArray, base: Int, length: Int): String {
            var offset = base // params are immutable
            val sb = StringBuilder()
            var value: Int = data[offset].toInt() and 0xFF
            sb.append(value / 40)
            sb.append('.')
            sb.append(value % 40)
            offset++
            value = 0
            for (i in 1 until length) {
                val delta: Int = data[offset].toInt() and 0x80
                val tmp: Int = data[offset].toInt() and 0xFF
                value *= 128
                value += tmp - delta
                if (delta == 0) {
                    sb.append('.')
                    sb.append(value)
                    value = 0
                }
                offset++
            }
            return sb.toString()
        }

        fun intValue(b: ByteArray): Int {
            var result = 0
            for (i in b.indices) {
                result *= 256
                result += b[i].toInt() and 0x00FF
            }
            return result
        }
    }
}