package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.djvm.proto.StringList
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SandboxThrowableJavaTest {

    class TestUserExceptionHandling : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(listOf("FIRST FINALLY", "BASE EXCEPTION", "Hello World!", "SECOND FINALLY"))
        }
    }

    class TestCheckedExceptions : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(listOf("/hello/world", "CATCH:Illegal character in path at index 5: nasty string"))
        }
    }

    class TestMultiCatchExceptions : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result[0]).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyExampleException:1")
            assertThat(result[1]).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyOtherException:2")
            assertThat(result[2]).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.BigTroubleException -> 3")
            assertThat(result[3]).isEqualTo("4")
            assertThat(result[4]).isEqualTo("Unknown")
        }
    }

    class TestMultiCatchWithDisallowedExceptions : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyExampleException:Hello World!")
        }
    }

    class TestSuppressedJvmExceptions : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result[0]).isEqualTo("READ=Hello World!")
            assertThat(result[1]).isEqualTo("READ=Hello World!")
            assertThat(result[2]).isEqualTo("CLOSING")
        }
    }

    class TestSuppressedUserExceptions : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result[0]).isEqualTo("THROW: Hello World!")
            assertThat(result[1]).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.MyExampleException -> THROW: Hello World!")
            assertThat(result[2]).isEqualTo("sandbox.com.r3.conclave.jvmtester.djvm.tests.BigTroubleException -> BadResource: Hello World!")
        }
    }
}