package com.r3.conclave.jvmtester.djvm.asserters

import com.google.protobuf.Int32Value
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SandboxObjectHashCodeJavaTest {

    class TestHashForArray : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int32Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(0xfed_c0de + 1)
        }
    }

    class TestHashForObjectInArray : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int32Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(0xfed_c0de + 1)
        }
    }

    class TestHashForNullObject : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(NullPointerException::class.java.canonicalName)
        }
    }

    class TestHashForWrappedInteger : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int32Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(Integer.hashCode(1234))
        }
    }

    class TestHashForWrappedString : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int32Value.parseFrom(testResult).value
            assertThat(result).isEqualTo("Burble".hashCode())
        }
    }
}