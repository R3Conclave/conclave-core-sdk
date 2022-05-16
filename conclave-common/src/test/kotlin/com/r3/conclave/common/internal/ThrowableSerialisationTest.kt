package com.r3.conclave.common.internal

import com.r3.conclave.utilities.internal.nullableWrite
import com.r3.conclave.utilities.internal.writeData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

class ThrowableSerialisationTest {
    @ParameterizedTest(name = "{displayName} withMessage='{0}' withCause='{1}'")
    @ArgumentsSource(BooleanPermutations::class)
    fun `message and cause c'tor`(withMessage: Boolean, withCause: Boolean) {
        val original = MessageAndCauseException(
            "message here".takeIf { withMessage },
            Exception("da cause").takeIf { withCause }
        )
        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @ParameterizedTest(name = "{displayName} withMessage='{0}' withCause='{1}'")
    @ArgumentsSource(BooleanPermutations::class)
    fun `cause and message c'tor`(withMessage: Boolean, withCause: Boolean) {
        val original = CauseAndMessageException(
            Exception("da cause").takeIf { withCause },
            "message here".takeIf { withMessage }
        )
        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @ParameterizedTest(name = "{displayName} withMessage='{0}' withCause='{1}'")
    @ArgumentsSource(BooleanPermutations::class)
    fun `message only c'tor`(withMessage: Boolean, withCause: Boolean) {
        val original = MessageOnlyException("message here".takeIf { withMessage })
            .initCause(Exception("da cause").takeIf { withCause })
        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @ParameterizedTest(name = "{displayName} withCause='{0}'")
    @ValueSource(booleans = [true, false])
    fun `cause only c'tor`(withCause: Boolean) {
        val original = CauseOnlyException(Exception("da cause").takeIf { withCause })
        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @ParameterizedTest(name = "{displayName} withCause='{0}'")
    @ValueSource(booleans = [true, false])
    fun `cause and constructed message c'tor`(withCause: Boolean) {
        val original = CauseWithConstructedMessageException(Exception("da cause").takeIf { withCause })
        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @ParameterizedTest(name = "{displayName} withCause='{0}'")
    @ValueSource(booleans = [true, false])
    fun `empty c'tor`(withCause: Boolean) {
        val original = EmptyException().initCause(Exception("da cause").takeIf { withCause })
        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @ParameterizedTest(name = "{displayName} withCause='{0}'")
    @ValueSource(booleans = [true, false])
    fun `empty with constructed message c'tor`(withCause: Boolean) {
        val original = EmptyWithConstructedMessageException().initCause(Exception("da cause").takeIf { withCause })
        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @ParameterizedTest(name = "{displayName} withMessage='{0}' withCause='{1}'")
    @ArgumentsSource(BooleanPermutations::class)
    fun `suppressed exceptions`(withMessage: Boolean, withCause: Boolean) {
        val original = Exception()
        repeat(2) {
            original.addSuppressed(
                Exception("message here $it".takeIf { withMessage }, Exception("da cause").takeIf { withCause })
            )
        }

        val roundtrip = ThrowableSerialisation.deserialise(ThrowableSerialisation.serialise(original))
        assertExceptionEqual(roundtrip, original)
    }

    @Test
    fun `deserialising unknown exception`() {
        val serialised = writeData {
            writeInt(1) // Cause chain count
            writeUTF("com.foo.bar.Exception")
            nullableWrite("BOOM") { writeUTF(it) }
            writeInt(0) // Stacktrace
            writeInt(0) // Suppressed exceptions
        }

        val deserialised = ThrowableSerialisation.deserialise(serialised)
        assertThat(deserialised).isExactlyInstanceOf(RuntimeException::class.java)
        assertThat(deserialised).hasMessage("com.foo.bar.Exception: BOOM")
        assertThat(deserialised).hasNoCause()
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
        if (expected.cause != null) {
            assertNotNull(actual.cause) { "Expected $actual to have a cause but it doesn't" }
            assertExceptionEqual(actual.cause!!, expected.cause!!)
        } else {
            assertThat(actual).hasNoCause()
        }
    }

    class MessageAndCauseException(message: String?, cause: Throwable?) : Exception(message, cause)
    class CauseAndMessageException(cause: Throwable?, message: String?) : Exception(message, cause)
    class MessageOnlyException(message: String?) : Exception(message)
    class CauseOnlyException(cause: Throwable?) : Exception(cause)
    class CauseWithConstructedMessageException(cause: Throwable?) : Exception("Constructed message", cause)
    class EmptyException : Exception()
    class EmptyWithConstructedMessageException : Exception("Constructed message")

    class BooleanPermutations : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false)
            )
        }
    }
}
