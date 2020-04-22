package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SecurityManagerTest {

    class TestReplacingSecurityManager : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).isEqualTo("sandbox.java.security.AccessControlException -> access denied")
        }
    }
}