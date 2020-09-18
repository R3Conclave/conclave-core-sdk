package com.r3.conclave.integrationtests.djvm.sandboxtests.asserters

import com.r3.conclave.integrationtests.djvm.base.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class MaliciousClassTest {

    class TestImplementingToDJVMString : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).contains("Class is not allowed to implement toDJVMString()")
        }
    }

    class TestImplementingFromDJVM : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).contains("Class is not allowed to implement fromDJVM()")
        }
    }

    class TestPassingClassIntoSandboxIsForbidden : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).contains("Cannot sandbox class java.lang.String")
        }
    }

    class TestPassingConstructorIntoSandboxIsForbidden : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage)
                    .contains("Cannot sandbox public com.r3.conclave.integrationtests.djvm.sandboxtests.MaliciousClassTest\$TestPassingConstructorIntoSandboxIsForbiddenEnclaveTest()")
        }
    }

    class TestPassingClassLoaderIntoSandboxIsForbidden : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).contains("Cannot sandbox a ClassLoader")
        }
    }

    /**
     * Fails due to compile-time dependency on sandbox.java.lang.Comparable, which is excluded from the DJVM jar
     */
//    class TestCannotInvokeSandboxMethodsExplicitly : DJVMTestAsserter {
//        override fun assertResult(testResult: TestResult) {
//            assertThat(testResult.hasResult()).isFalse()
//            assertThat(testResult.hasExceptionMessage()).isTrue()
//            assertThat(testResult.exceptionMessage)
//                    .contains(Type.getInternalName(MaliciousClassTest.SelfSandboxing::class.java))
//                    .contains("Access to sandbox.java.lang.String.toDJVM(String) is forbidden.")
//                    .contains("Access to sandbox.java.lang.String.fromDJVM(String) is forbidden.")
//                    .contains("Casting to sandbox.java.lang.String is forbidden.")
//        }
//    }
}