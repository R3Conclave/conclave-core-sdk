package com.r3.conclave.jvmtester.djvm.asserters

import com.r3.conclave.jvmtester.djvm.proto.StringList
import com.r3.conclave.jvmtester.api.TestAsserter
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