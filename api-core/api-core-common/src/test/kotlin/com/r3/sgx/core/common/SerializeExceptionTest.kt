package com.r3.sgx.core.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.Exception

class SerializeExceptionTest {
    @Test
    fun `round trip with message`() {
        val exception = Exception("BANG")
        val protobuf = SerializeException.javaToProtobuf(exception)
        val roundtrip = SerializeException.protobufToJava(protobuf)
        assertExceptionEqual(roundtrip, exception)
    }

    @Test
    fun `round trip with no message`() {
        val exception = Exception()
        val protobuf = SerializeException.javaToProtobuf(exception)
        val roundtrip = SerializeException.protobufToJava(protobuf)
        assertExceptionEqual(roundtrip, exception)
    }

    @Test
    fun `round trip for exception class with no message constructor`() {
        val exception = NoMessageException()
        val protobuf = SerializeException.javaToProtobuf(exception)
        val roundtrip = SerializeException.protobufToJava(protobuf)
        assertExceptionEqual(roundtrip, exception)
    }

    @Test
    fun `deserialising unknown exception`() {
        val protobuf = ProtobufException.newBuilder()
                .setExceptionClass("com.foo.bar.Exception")
                .setMessage("BOOM")
                .build()
        val deserialised = SerializeException.protobufToJava(protobuf)
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
