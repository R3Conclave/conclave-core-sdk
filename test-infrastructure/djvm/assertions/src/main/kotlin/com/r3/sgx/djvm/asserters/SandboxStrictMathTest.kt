package com.r3.sgx.djvm.asserters

import com.r3.sgx.djvm.proto.TestStrictMaxMinParameters
import com.r3.sgx.test.assertion.TestAsserter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within

class SandboxStrictMathTest {

    class TestStrictMathHasNoRandom : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val exceptionMessage = String(testResult)
            assertThat(exceptionMessage)
                    .contains("random")
        }
    }

    class TestStrictMathHasTrigonometry : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = com.r3.sgx.djvm.proto.DoubleArray.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(arrayOf(
                    0.0,
                    -1.0,
                    0.0,
                    StrictMath.PI / 2.0,
                    StrictMath.PI / 2.0,
                    0.0,
                    StrictMath.PI / 4.0
            ).toList())
        }
    }

    class TestStrictMathRoots : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = com.r3.sgx.djvm.proto.DoubleArray.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(arrayOf(8.0, 4.0, 13.0).toList())
        }
    }

    class TestStrictMaxMin : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = TestStrictMaxMinParameters.parseFrom(testResult)
            assertThat(result.d1).isEqualTo(100.0)
            assertThat(result.d2).isEqualTo(0.0)
            assertThat(result.f1).isEqualTo(100.0f)
            assertThat(result.f2).isEqualTo(0.0f)
            assertThat(result.l1).isEqualTo(100L)
            assertThat(result.l2).isEqualTo(0L)
            assertThat(result.i1).isEqualTo(100)
            assertThat(result.i2).isEqualTo(0)
        }
    }

    class TestStrictAbsolute : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = TestStrictMaxMinParameters.parseFrom(testResult)
            assertThat(result.d1).isEqualTo(100.0)
            assertThat(result.f1).isEqualTo(100.0f)
            assertThat(result.l1).isEqualTo(100L)
            assertThat(result.i1).isEqualTo(100)
        }
    }

    class TestStrictRound : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = TestStrictMaxMinParameters.parseFrom(testResult)
            assertThat(result.i1).isEqualTo(2019)
            assertThat(result.l1).isEqualTo(2019L)
            assertThat(result.i2).isEqualTo(2021)
            assertThat(result.l2).isEqualTo(2021L)
        }
    }

    class TestStrictExponents : TestAsserter {
        companion object {
            private val ERROR_DELTA = 1.0E-10
        }

        override fun assertResult(testResult: ByteArray) {
            val result = com.r3.sgx.djvm.proto.DoubleArray.parseFrom(testResult).valuesList
            assertThat<Double>(result).hasSize(6)
            assertThat(result[0]).isEqualTo(81.0)
            assertThat(result[1]).isEqualTo(1.0)
            assertThat(result[2]).isEqualTo(3.0)
            assertThat(result[3]).isEqualTo(StrictMath.E, within(ERROR_DELTA))
            assertThat(result[4]).isEqualTo(StrictMath.E - 1.0, within(ERROR_DELTA))
            assertThat(result[5]).isEqualTo(1.0, within(ERROR_DELTA))
        }
    }

    class TestStrictAngles : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = com.r3.sgx.djvm.proto.DoubleArray.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(arrayOf(180.0, StrictMath.PI).toList())
        }
    }

    class TestStrictHyperbolics : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = com.r3.sgx.djvm.proto.DoubleArray.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(arrayOf(0.0, 1.0, 0.0).toList())
        }
    }

    class TestStrictRemainder : TestAsserter {
        override fun assertResult(testResult: ByteArray) {
            val result = com.r3.sgx.djvm.proto.DoubleArray.parseFrom(testResult).valuesList
            assertThat(result).isEqualTo(arrayOf(3.0, 0.0, -2.0).toList())
        }
    }
}