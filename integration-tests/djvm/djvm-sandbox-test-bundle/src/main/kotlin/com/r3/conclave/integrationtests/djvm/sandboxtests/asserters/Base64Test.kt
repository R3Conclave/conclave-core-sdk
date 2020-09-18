package com.r3.conclave.integrationtests.djvm.sandboxtests.asserters

import com.r3.conclave.integrationtests.djvm.base.TestAsserter
import org.assertj.core.api.Assertions.assertThat
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*

class Base64Test {
    companion object {
        val MESSAGE = "Round and round the rugged rocks..."
        val BASE64 = Base64.getEncoder().encodeToString(MESSAGE.toByteArray(UTF_8))
    }

    class Base64ToBinaryTest : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(MESSAGE)
        }
    }

    class BinaryToBase64Test : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = String(testResult)
            assertThat(result).isEqualTo(BASE64)
        }
    }
}