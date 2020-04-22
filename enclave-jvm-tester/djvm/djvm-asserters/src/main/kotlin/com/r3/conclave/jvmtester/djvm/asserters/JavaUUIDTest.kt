package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class JavaUUIDTest {
    class UUIDTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result.length).isGreaterThan(0)
        }
    }
}