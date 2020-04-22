package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SandboxCloneableTest {
    class TestCloningInsideSandbox : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("Jango Fett")
        }
    }

    class TestFailedCloningInsideSandbox : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage)
                    .contains("sandbox.com.r3.conclave.jvmtester.djvm.tests.SandboxCloneableTest\$Jedi")
        }
    }
}