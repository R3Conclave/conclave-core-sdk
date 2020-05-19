package com.r3.conclave.utils.internal

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class ByteBufferInputStreamTest {
    companion object {
        val data = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
    }

    private lateinit var stream: ByteBufferInputStream

    @Before
    fun setup() {
        stream = ByteBufferInputStream(ByteBuffer.wrap(data))
    }

    @Test
    fun testAvailable() {
        assertEquals(data.size, stream.available())
    }

    @Test
    fun testSkip() {
        val skipped = stream.skip(3)
        assertEquals(3, skipped)

        // Check actual number of skipped bytes is returned when we "over-skip"
        val availableToSkip = stream.available().toLong()
        assertEquals(availableToSkip, stream.skip(availableToSkip + 1))
    }

    @Test
    fun testMarkSupported() {
        assertTrue(stream.markSupported())
    }

    @Test
    fun testMarkAndReset() {
        // Confirm stream starts containing all bytes
        assertEquals(data.size, stream.available())

        // Advance the position by two bytes
        assertEquals(0x00, stream.read())
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