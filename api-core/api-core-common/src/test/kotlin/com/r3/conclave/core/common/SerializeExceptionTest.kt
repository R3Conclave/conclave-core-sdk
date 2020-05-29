package com.r3.conclave.core.common

import com.r3.conclave.common.internal.nullableWrite
import com.r3.conclave.common.internal.writeData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SerializeExceptionTest {
    @Test
    fun `round trip with message`() {
        val original = Exception("BANG")
        val roundtrip = SerializeException.deserialise(SerializeException.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @Test
    fun `round trip with no message`() {
        val original = Exception()
        val roundtrip = SerializeException.deserialise(SerializeException.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @Test
    fun `round trip for exception class with no message constructor`() {
        val original = NoMessageException()
        val roundtrip = SerializeException.deserialise(SerializeException.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @Test
    fun `deserialising unknown exception`() {
        val serialised = writeData {
            writeUTF("com.foo.bar.Exception")
            nullableWrite("BOOM") { writeUTF(it) }
            writeInt(0)
        }

        val deserialised = SerializeException.deserialise(serialised)
        assertThat(deserialised).isExactlyInstanceOf(RuntimeException::class.java)
        assertThat(deserialised).hasMessage("com.foo.bar.Exception: BOOM")
    }

    private fun assertExceptionEqual(actual: Throwable, expected: Throwable) {
        assertThat(actual).isExactlyInstanceOf(expected.javaClass)
        assertThat(actual).hasMessage(expected.message)
        assertThat(actual.stackTrace).isEqualTo(expected.stackTrace)
    }

    class NoMessageException : Exception()
}
