package com.r3.conclave.integrationtests.djvm.simpletests

import com.google.protobuf.BoolValue
import com.google.protobuf.Int64Value
import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest
import com.r3.conclave.integrationtests.djvm.base.TestSerializable
import org.assertj.core.api.Assertions.assertThat

class Longs {
    class PositiveComparisonEnclaveEnclaveTest : EnclaveJvmTest {
        override fun apply(input: Any?): Any {
            val l1 = 0L
            val l2 = 1L
            return l2 > l1
        }

        override fun serializeTestOutput(output: Any?): ByteArray {
            return BoolValue.newBuilder().setValue(output as Boolean).build().toByteArray()
        }

        override fun assertResult(testResult: ByteArray) {
            assertThat(BoolValue.parseFrom(testResult).value).isTrue()
        }
    }

    class NegativeComparisonEnclaveEnclaveTest : EnclaveJvmTest, TestSerializable {
        override fun deserializeTestInput(data: ByteArray): Any = Int64Value.parseFrom(data).value

        override fun getTestInput(): ByteArray = Int64Value.newBuilder().setValue(-2L).build().toByteArray()

        override fun apply(input: Any?): Any {
            val l1 = 0L
            val l2 : Long = input as Long
            return l2 > l1
        }

        override fun serializeTestOutput(output: Any?): ByteArray {
            return BoolValue.newBuilder().setValue(output as Boolean).build().toByteArray()
        }

        override fun assertResult(testResult: ByteArray) {
            assertThat(BoolValue.parseFrom(testResult).value).isFalse()
        }
    }
}