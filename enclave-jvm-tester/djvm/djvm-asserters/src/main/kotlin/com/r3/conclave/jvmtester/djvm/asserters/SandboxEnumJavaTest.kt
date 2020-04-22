package com.r3.conclave.jvmtester.djvm.asserters

import com.google.protobuf.BoolValue
import com.google.protobuf.Int32Value
import com.r3.conclave.jvmtester.djvm.testsauxiliary.ExampleEnum
import com.r3.conclave.jvmtester.djvm.proto.StringList
import com.r3.conclave.jvmtester.api.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SandboxEnumJavaTest {
    class TestEnumInsideSandbox : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList.toTypedArray()
            assertThat(result).isEqualTo(arrayOf("ONE", "TWO", "THREE"))
        }
    }

    class TestReturnEnumFromSandbox : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(ExampleEnum.THREE.toString())
        }
    }

    class TestWeCanIdentifyClassAsEnum : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = BoolValue.parseFrom(testResult).value
            assertThat(result).isTrue()
        }
    }

    class TestWeCanCreateEnumMap : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = Int32Value.parseFrom(testResult).value
            assertThat(result).isEqualTo(1)
        }
    }

    class TestWeCanReadConstantEnum : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(ExampleEnum.ONE.toString())
        }
    }

    class TestWeCanReadStaticConstantEnum : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(ExampleEnum.TWO.toString())
        }
    }
}