package com.r3.sgx.djvm.asserters

import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SandboxExecutorJavaTest {

    class TestTransaction : TestAsserter {
        private val TX_ID = 101

        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).contains("Contract constraint violated: txId=$TX_ID")
        }
    }
}