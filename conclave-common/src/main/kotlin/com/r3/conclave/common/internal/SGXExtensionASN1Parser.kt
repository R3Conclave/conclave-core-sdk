package com.r3.conclave.common.internal

import java.nio.ByteBuffer
import java.util.*

// see Intel's docs for IODs - 1.3.5 Intel® SGX PCK Certificate
// https://download.01.org/intel-sgx/latest/dcap-latest/linux/docs/Intel_SGX_PCK_Certificate_CRL_Spec-1.4.pdf

// ASN1 types described here https://docs.microsoft.com/en-us/windows/win32/seccertenroll/about-octet-string

class SGXExtensionASN1Parser(private val data: ByteArray) {
    private val _values = HashMap<String, ValuePos>()
    private val _types = HashMap<String, Int>()
    internal var offset = 0

    // parse top-level octet string
    // do not decode fields that are octet strings
    init {
        require(data.size >= 2) { "Octet string too short: ${data.size}" }
        val tag = readUByte()
        require(tag == 0x04) { String.format("octet_string: invalid tag 0x%02X", tag) }
        decode()
    }

    fun getBytes(id: String): ByteBuffer {
        val position = _values.getValue(id)
        return ByteBuffer.wrap(data, position.offset, position.length)
    }

    fun getInt(id: String): Int {
        val position = _values.getValue(id)
        var result = 0
        repeat(position.length) { i ->
            result *= 256
            result += data[position.offset + i].toInt() and 0x00FF
        }
        return result
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

    private fun decode() {
        val length = readLength()
        val end = offset + length
        /*
         * KV pairs are encoded as sequences
         */
        var key: String? = null
        var value: ValuePos? = null
        var type = 0
        while (offset < end) {
            val tag = readUByte()
            when (tag) {
                SEQ_TAG -> {
                    decode()
                    key = null
                    value = null
                    type = 0
                }
                OID_TAG -> {
                    key = readObjectIDString()
                }
                OCTSTR_TAG, INT_TAG, ENUM_TAG -> {
                    value = valuePos()
                    type = tag
                }
                else -> throw IllegalArgumentException(String.format("unknown tag 0x%02X", tag))
            }
            if (key != null && value != null) {
                _values[key] = value
                _types[key] = type
            }
        }
    }

    private fun readLength(): Int {
        var length = readUByte()
        if (length > 127) {
            val cnt = length and 0x7F
            length = 0
            repeat(cnt) {
                length *= 256
                length += readUByte()
            }
        }
        return length
    }

    private fun readObjectIDString(): String {
        val length = readLength()

        val sb = StringBuilder()
        var value = readUByte()
        sb.append(value / 40)
        sb.append('.')
        sb.append(value % 40)
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

    private fun valuePos(): ValuePos {
        val length = readLength()
        val valuePos = ValuePos(offset, length)
        offset += length
        return valuePos
    }

    private fun readUByte(): Int = data[offset++].toInt() and 0xFF

    private class ValuePos(val offset: Int, val length: Int)

    companion object {
        private const val SEQ_TAG = 0x30
        private const val OID_TAG = 0x06
        private const val OCTSTR_TAG = 0x04
        private const val INT_TAG = 0x02
        private const val ENUM_TAG = 0x0A
    }
}
