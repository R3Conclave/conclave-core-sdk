package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.djvm.proto.IntArray
import com.r3.conclave.jvmtester.djvm.proto.StringList
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SandboxStringTest {

    class TestJoiningIterableInsideSandbox : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("one+two+three")
        }
    }

    class TestJoiningVarargInsideSandbox : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("ONE+TWO+THREE")
        }
    }

    class TestStringConstant : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo("Wibble!")
        }
    }

    class EncodeStringWithUnknownCharset : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage).isEqualTo("Nonsense-101")
        }
    }

    class DecodeStringWithCharset : TestAsserter {
        companion object {
            private const val UNICODE_MESSAGE = "Goodbye, Cruel World! \u1F4A9"
        }

        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(listOf(UNICODE_MESSAGE, UNICODE_MESSAGE, UNICODE_MESSAGE))
        }
    }

    class TestCaseInsensitiveComparison : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = IntArray.parseFrom(testResult).valuesList
            assertThat(result[0]).isEqualTo(0)
            assertThat(result[1]).isLessThan(0)
            assertThat(result[2]).isGreaterThan(0)
        }
    }

    class TestStream : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            
            
            val result = String(testResult)
            assertThat(result).isEqualTo("{dog + cat + mouse + squirrel}")
        }
    }

    class TestSorting : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            
            
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).containsExactly("CAT", "PIG", "TREE", "WOLF")
        }
    }

    class TestComplexStream : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            
            
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).containsExactly("ONE", "TWO", "THREE", "FOUR", "FIVE")
        }
    }

    class TestSpliterator : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            
            
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).containsExactlyInAnyOrder("one+two", "three+four")
        }
    }
}