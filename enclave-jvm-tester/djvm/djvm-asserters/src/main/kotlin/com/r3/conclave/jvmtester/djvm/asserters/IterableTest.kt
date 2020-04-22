package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class IterableTest {
    class Create : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("sandbox.java.util.ArrayList.Itr")
        }
    }
}