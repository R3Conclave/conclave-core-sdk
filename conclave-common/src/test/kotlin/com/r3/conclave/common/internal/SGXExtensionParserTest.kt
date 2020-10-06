package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.parseHex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SGXExtensionParserTest {
    private val parser = SGXExtensionASN1Parser()

    @Test
    fun `input too short`() {
        val data = parseHex("00")

        val exception = assertThrows<java.lang.Exception> {  parser.parse(data,data.size) }
        assertTrue(exception.message!!.contains("too short"))
    }

    @Test
    fun `invalid sequence tag`() {
        val data = parseHex("0000")

        val exception = assertThrows<java.lang.Exception> {  parser.parse(data,data.size) }
        assertTrue(exception.message!!.contains("invalid tag"))
    }

    @Test
    fun `empty octet string`() {
        val data = parseHex("0400")

        assertTrue(parser.parse(data,data.size))
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence without KV`() {
        val data = parseHex("04023000")

        assertTrue(parser.parse(data,data.size))
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence without K`() {
        val data = parseHex("04053003020101") // integer=1

        assertTrue(parser.parse(data,data.size))
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence without V`() {
        val data = parseHex("04053003060101") // key=0.1

        assertTrue(parser.parse(data,data.size))
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence with valid KV (int)`() {
        val data = parseHex("04083006060101020103") // key=0.1 val(int)=3

        assertTrue(parser.parse(data,data.size))
        assertTrue(parser.keys().contains("0.1"))
        assertEquals(parser.value("0.1").size, 1)
        assertEquals(parser.value("0.1")[0],3)
    }

    @Test
    fun `sequence of sequences with valid KV (int)`() {
        val data = parseHex("040A30083006060101020103") // key=0.1 val(int)=3

        assertTrue(parser.parse(data,data.size))
        assertTrue(parser.keys().contains("0.1"))
        assertEquals(parser.value("0.1").size, 1)
        assertEquals(parser.value("0.1")[0],3)
    }
}