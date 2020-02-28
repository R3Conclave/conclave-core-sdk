package com.r3.sgx.djvm.asserters

import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class IterableTest {
    class Create : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("sandbox.java.util.ArrayList.Itr")
        }
    }
}