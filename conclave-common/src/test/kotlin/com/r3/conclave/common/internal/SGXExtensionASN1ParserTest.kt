package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.parseHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class SGXExtensionASN1ParserTest {
    @Test
    fun `input too short`() {
        val data = parseHex("00")

        val exception = assertThrows<IllegalArgumentException> { SGXExtensionASN1Parser(data) }
        assertTrue(exception.message!!.contains("too short"))
    }

    @Test
    fun `invalid sequence tag`() {
        val data = parseHex("0000")

        val exception = assertThrows<IllegalArgumentException> { SGXExtensionASN1Parser(data) }
        assertTrue(exception.message!!.contains("invalid tag"))
    }

    @Test
    fun `empty octet string`() {
        val data = parseHex("0400")
        val parser = SGXExtensionASN1Parser(data)

        assertThat(parser.offset).isEqualTo(data.size)
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence without KV`() {
        val data = parseHex("04023000")
        val parser = SGXExtensionASN1Parser(data)

        assertThat(parser.offset).isEqualTo(data.size)
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence without K`() {
        val data = parseHex("04053003020101") // integer=1
        val parser = SGXExtensionASN1Parser(data)

        assertThat(parser.offset).isEqualTo(data.size)
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence without V`() {
        val data = parseHex("04053003060101") // key=0.1
        val parser = SGXExtensionASN1Parser(data)

        assertThat(parser.offset).isEqualTo(data.size)
        assertTrue(parser.keys().isEmpty())
    }

    @Test
    fun `sequence with valid KV (int)`() {
        val data = parseHex("04083006060101020103") // key=0.1 val(int)=3
        val parser = SGXExtensionASN1Parser(data)

        assertThat(parser.offset).isEqualTo(data.size)
        assertTrue(parser.keys().contains("0.1"))
        assertThat(parser.getBytes("0.1")).isEqualTo(ByteBuffer.wrap(byteArrayOf(3)))
    }

    @Test
    fun `sequence of sequences with valid KV (int)`() {
        val data = parseHex("040A30083006060101020103") // key=0.1 val(int)=3
        val parser = SGXExtensionASN1Parser(data)

        assertThat(parser.offset).isEqualTo(data.size)
        assertThat(parser.keys()).contains("0.1")
        assertThat(parser.getBytes("0.1")).isEqualTo(ByteBuffer.wrap(byteArrayOf(3)))
    }

    @Test
    fun `duplicate key @ values`() {
        val data = parseHex("0412301030060601010201033006060101020105") // [0.1]=3, [0.1]=5
        val exception = assertThrows<IllegalArgumentException> { SGXExtensionASN1Parser(data) }
        assertThat(exception.message).contains("values: duplicate key")
    }

    @Test
    fun `getting Int from OctoString fails`() {
        val data = parseHex("040A30083006060101040130") // [0.1]='0'
        val parser = SGXExtensionASN1Parser(data)
        val exception = assertThrows<IllegalArgumentException> { parser.getInt("0.1") }
        assertThat(exception.message).contains("is not an integer or an enum")
    }

    @Test
    fun `get boolean from int fails`() {
        val data = parseHex("04083006060101040130")
        val parser = SGXExtensionASN1Parser(data)
        val exception = assertThrows<IllegalArgumentException> { parser.getBoolean("0.1") }
        assertThat(exception.message).contains("is not boolean")
    }

    @Test
    fun `get boolean FALSE`() {
        val data = parseHex("04083006060101010100")
        val parser = SGXExtensionASN1Parser(data)
        assertThat(parser.getBoolean("0.1")).isFalse
    }

    @Test
    fun `get boolean TRUE`() {
        val data = parseHex("04083006060101010101")
        val parser = SGXExtensionASN1Parser(data)
        assertThat(parser.getBoolean("0.1")).isTrue
    }
}
