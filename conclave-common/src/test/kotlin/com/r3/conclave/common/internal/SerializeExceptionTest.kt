package com.r3.conclave.common.internal

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
        val actualStackTrace = actual.stackTrace
        val expectedStackTrace = expected.stackTrace
        assertThat(actualStackTrace).hasSameSizeAs(expectedStackTrace)
        actualStackTrace.indices.forEach {
            // We intentionally check these four properties to avoid failing in Java 9 which introduces some more.
            // For now we don't care for the Java 9 properties as the exception serialisaion is only done between the
            // host and enclave (which only supports Java 8).
            assertThat(actualStackTrace[it].className).isEqualTo(expectedStackTrace[it].className)
            assertThat(actualStackTrace[it].methodName).isEqualTo(expectedStackTrace[it].methodName)
            assertThat(actualStackTrace[it].fileName).isEqualTo(expectedStackTrace[it].fileName)
            assertThat(actualStackTrace[it].lineNumber).isEqualTo(expectedStackTrace[it].lineNumber)
        }
    }

    class NoMessageException : Exception()
}
