package com.r3.sgx.djvm.asserters

import com.google.protobuf.BoolValue
import com.r3.sgx.djvm.proto.StringList
import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class JavaPackageTest {
    class FetchingPackageTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = BoolValue.parseFrom(testResult).value
            assertThat(result).isTrue()
        }
    }

    class FetchingAllPackagesTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).isEmpty()
        }
    }
}
