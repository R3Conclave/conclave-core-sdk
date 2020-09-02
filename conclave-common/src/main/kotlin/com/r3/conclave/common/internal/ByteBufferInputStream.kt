package com.r3.conclave.common.internal

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.InvalidMarkException
import kotlin.math.min

/**
 * [InputStream] which wraps over the remaining bytes of a [ByteBuffer]. Reading from the stream will advance
 * the buffer's position accordingly.
 */
class ByteBufferInputStream(private val bytes: ByteBuffer) : InputStream() {
    private companion object {
        private const val EOF = -1
    }

    override fun read(): Int = if (bytes.hasRemaining()) bytes.get().toInt() and 0xFF else EOF

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return if ((offset < 0) || (length < 0) || offset + length > buffer.size) {
            throw IndexOutOfBoundsException()
        } else {
            val bytesRemaining = bytes.remaining()
            if (bytesRemaining > 0) {
                val bytesRead = min(length, bytesRemaining)
                bytes.get(buffer, offset, bytesRead)
                bytesRead
            } else {
                EOF
            }
        }
    }

    override fun available(): Int = bytes.remaining()

    override fun markSupported(): Boolean = true

    override fun mark(readLimit: Int) {
        // Ignore the readLimit parameter because it
        // seems more applicable to an unlimited stream
        // than to a limited ByteBuffer.
        bytes.mark()
    }

    override fun reset() {
        try {
            bytes.reset()
        } catch (e: InvalidMarkException) {
            throw IOException("mark has not been called")
        }
    }
}
