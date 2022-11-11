package com.r3.conclave.integrationtests.general.tests.plugin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class GenerateReflectionConfigTest : AbstractConclaveTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val taskName: String get() = "generateReflectionConfig"
    override val outputFileName: String get() = "reflectconfig"

    @Test
    fun `task re-runs on enclave class name change`() {
        assertTaskIsIncremental {
            assertThat(reflectionConfig().map { it["name"].textValue() }).contains("com.test.enclave.TestEnclave")
            val enclaveCode = projectDir.resolve("src/main/kotlin/com/test/enclave/EnclaveTest.kt")
            enclaveCode.searchAndReplace("TestEnclave", "TestEnclave2")
        }
        assertThat(reflectionConfig().map { it["name"].textValue() })
            .contains("com.test.enclave.TestEnclave2")
            .doesNotContain("com.test.enclave.TestEnclave")
    }

    private fun reflectionConfig(): JsonNode = ObjectMapper().readTree(outputFile.toFile())
}
