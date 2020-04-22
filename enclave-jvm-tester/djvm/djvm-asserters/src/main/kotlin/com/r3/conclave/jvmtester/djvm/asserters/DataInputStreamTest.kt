package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.djvm.proto.ReadingDataTestResult
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class DataInputStreamTest {
    class ReadingDataTest : TestAsserter {
        private val MESSAGE = "Hello World!"
        private val BIG_FRACTION = 97323.38238232
        private val BIG_NUMBER = 81738392L
        private val NUMBER = 123456

        override fun assertResult(testResult: ByteArray) {
            val result = ReadingDataTestResult.parseFrom(testResult)
            assertThat(result.bigNumber).isEqualTo(BIG_NUMBER)
            assertThat(result.number).isEqualTo(NUMBER)
            assertThat(result.message).isEqualTo(MESSAGE)
            assertThat(result.bigFraction).isEqualTo(BIG_FRACTION)
        }
    }
}