package com.r3.sgx.core.common

import com.r3.conclave.common.internal.nullableWrite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

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
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeUTF("com.foo.bar.Exception")
        dos.nullableWrite("BOOM") { writeUTF(it) }
        dos.writeInt(0)
        val serialised = baos.toByteArray()

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
