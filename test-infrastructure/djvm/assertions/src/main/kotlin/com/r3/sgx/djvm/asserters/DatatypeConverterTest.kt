package com.r3.sgx.djvm.asserters

import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class DatatypeConverterTest {
    companion object {
        val BINARY = byteArrayOf(0x1f, 0x2e, 0x3d, 0x4c, 0x5b, 0x6a, 0x70)
        val TEXT = "1F2E3D4C5B6A70"
    }

    class BinaryToHexTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(TEXT)
        }
    }

    class HexToBinaryTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            assertThat(testResult).isEqualTo(BINARY)
        }
    }
}