package com.r3.conclave.integrationtests.djvm.sandboxtests.asserters

import com.google.protobuf.Int64Value
import com.r3.conclave.integrationtests.djvm.base.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class BasicInputOutputTest {
    companion object {
        val MESSAGE = "Hello World!"
        val BIG_NUMBER = 123456789000L
    }

    class BasicInput : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(BasicInputOutputTest.MESSAGE)
        }
    }

    class BasicOutput : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int64Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(BasicInputOutputTest.BIG_NUMBER)
        }
    }

    class ImportTask: TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(String.format(">>> %s <<<", BasicInputOutputTest.MESSAGE))
        }

    }
}
