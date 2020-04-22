package com.r3.conclave.jvmtester.djvm.asserters

import com.google.protobuf.Int64Value
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class CapturingLambdaTest {
    companion object {
        private const val BIG_NUMBER = 1234L
        private const val MULTIPLIER = 100
    }
    class TestCapturingLambda : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int64Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(BIG_NUMBER * MULTIPLIER)
        }
    }
}