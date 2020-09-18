package com.r3.conclave.integrationtests.djvm.sandboxtests.asserters

import com.r3.conclave.integrationtests.djvm.base.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class JavaUUIDTest {
    class UUIDTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result.length).isGreaterThan(0)
        }
    }
}