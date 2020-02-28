package com.r3.sgx.djvm.asserters

import com.r3.sgx.djvm.proto.StringList
import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class JavaChronologyTest {
    class AvailableChronologiesTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = StringList.parseFrom(testResult).valuesList
            assertThat(result).contains(
                    "Hijrah-umalqura",
                    "ISO",
                    "Japanese",
                    "Minguo",
                    "ThaiBuddhist"
            )
        }
    }
}