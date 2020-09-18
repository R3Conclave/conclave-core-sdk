package com.r3.conclave.integrationtests.djvm.sandboxtests.asserters

import com.r3.conclave.integrationtests.djvm.base.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class MaliciousClassLoaderTest {

    class TestWithAnEvilClassLoader : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage)
                    .contains("currentTimeMillis")
        }
    }

    class TestWithEvilParentClassLoader : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage)
                    .contains("Disallowed reference to API; java.lang.ClassLoader(ClassLoader)")
        }
    }

    class TestAccessingParentClassLoader : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("net.corda.djvm.rewiring.SandboxClassLoader")
        }

    }

    class TestClassLoaderForWhitelistedClass : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("net.corda.djvm.rewiring.SandboxClassLoader")
        }
    }
}