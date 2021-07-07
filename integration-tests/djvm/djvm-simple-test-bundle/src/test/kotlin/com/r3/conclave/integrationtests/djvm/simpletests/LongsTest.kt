package com.r3.conclave.integrationtests.djvm.simpletests

import com.r3.conclave.integrationtests.djvm.base.EnclaveJvmTest
import com.r3.conclave.integrationtests.djvm.base.TestSerializable
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Disabled
class LongsTest {
    @ParameterizedTest
    @ValueSource(classes = [ Longs.PositiveComparisonEnclaveEnclaveTest::class, Longs.NegativeComparisonEnclaveEnclaveTest::class ])
    fun test(testClass: Class<out EnclaveJvmTest>) {
        val test = testClass.newInstance()
        val input = (test as? TestSerializable)?.deserializeTestInput(test.getTestInput())
        val result = test.apply(input)
        val resultSerialised = test.serializeTestOutput(result)
        test.assertResult(resultSerialised)
    }
}
