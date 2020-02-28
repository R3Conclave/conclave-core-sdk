package com.r3.sgx.djvm.asserters

import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SecurityManagerTest {

    class TestReplacingSecurityManager : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).isEqualTo("sandbox.java.security.AccessControlException -> access denied")
        }
    }
}