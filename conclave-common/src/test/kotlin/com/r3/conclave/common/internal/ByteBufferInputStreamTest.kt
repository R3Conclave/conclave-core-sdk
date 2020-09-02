package com.r3.conclave.common.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class ByteBufferInputStreamTest {
    companion object {
        val data = byteArrayOf(0x80.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
    }

    private lateinit var stream: ByteBufferInputStream

    @BeforeEach
    fun setup() {
        stream = ByteBufferInputStream(ByteBuffer.wrap(data))
    }

    @Test
    fun `read unsigned byte`() {
        assertThat(stream.read()).isEqualTo(0x80)
    }

    @Test
    fun `test available`() {
        assertEquals(data.size, stream.available())
    }

    @Test
    fun `test skip`() {
        val skipped = stream.skip(3)
        assertEquals(3, skipped)

        // Check actual number of skipped bytes is returned when we "over-skip"
        val availableToSkip = stream.available().toLong()
        assertEquals(availableToSkip, stream.skip(availableToSkip + 1))
    }

    @Test
    fun `test mark supported`() {
        assertTrue(stream.markSupported())
    }

    @Test
    fun `mark and reset`() {
        // Confirm stream starts containing all bytes
        assertEquals(data.size, stream.available())

        // Advance the position by two bytes
        assertEquals(0x80, stream.read())
        assertEquals(0x01, stream.read())

        val buffer = ByteArray(data.size - 5)

        // Mark the stream here - the readLimit value is ignored.
        stream.mark(-1)

        val remaining = stream.available()

        // Fill the buffer from the stream
        assertEquals(buffer.size, stream.read(buffer))
        assertArrayEquals(byteArrayOf(0x02, 0x03, 0x04), buffer)
        assertEquals(remaining - buffer.size, stream.available())

        // Restore the stream's position to the mark
        stream.reset()
        assertEquals(remaining, stream.available())
        assertEquals(0x02, stream.read())
    }
}