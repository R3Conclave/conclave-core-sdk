package com.r3.conclave.integrationtests.djvm.sandboxtests.asserters

import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestCurrencyParameter
import com.r3.conclave.integrationtests.djvm.sandboxtests.proto.TestCurrencyParameterList
import com.r3.conclave.integrationtests.djvm.base.TestAsserter
import org.assertj.core.api.Assertions.assertThat

class SandboxCurrencyTest {

    class TestCurrencies : TestAsserter {
        val INPUT = TestCurrencyParameterList.newBuilder()
                .addValues(TestCurrencyParameter.newBuilder()
                        .setCurrencyCode("GBP")
                        .setDisplayName("British Pound Sterling")
                        .setSymbol("GBP")
                        .setFractionDigits(2).build())
                .addValues(TestCurrencyParameter.newBuilder()
                        .setCurrencyCode("EUR")
                        .setDisplayName("Euro")
                        .setSymbol("EUR")
                        .setFractionDigits(2).build())
                .addValues(TestCurrencyParameter.newBuilder()
                        .setCurrencyCode("USD")
                        .setDisplayName("US Dollar")
                        .setSymbol("USD")
                        .setFractionDigits(2).build())
                .build()

        override fun assertResult(testResult: ByteArray) {
            val result = TestCurrencyParameterList.parseFrom(testResult).valuesList
            assertThat(result.size).isEqualTo(INPUT.valuesCount)
            for (i in 0 until result.size) {
                val currencyParameter = result[i]
                val inputParameter = INPUT.valuesList[i]
                assertThat(currencyParameter.displayName).isEqualTo(inputParameter.displayName)
                assertThat(currencyParameter.symbol).isEqualTo(inputParameter.symbol)
                assertThat(currencyParameter.fractionDigits).isEqualTo(inputParameter.fractionDigits)
            }
        }
    }
}