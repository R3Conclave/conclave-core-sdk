package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.AvianTestRunner
import com.r3.conclave.integrationtests.general.common.tasks.testCases
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.stream.Stream

class AvianTestSuite : JvmTest(threadSafe = false) {

    class AvianTestArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return testCases.keys.stream().map { Arguments.of(it) }
        }
    }

    @ArgumentsSource(AvianTestArgumentProvider::class)
    @ParameterizedTest
    fun `avian test suite`(name: String) {
        val test = AvianTestRunner(name)
        val response = sendMessage(test)
        assertThat(response.message).isEqualTo(name)
        assertThat(response.success).isEqualTo(true)
    }
}
