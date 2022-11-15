package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EnclaveClassNameTest : AbstractConclaveTaskTest() {
    override val taskName: String get() = "enclaveClassName"
    override val outputName: String get() = "enclave-class-name.txt"

    @BeforeEach
    fun graalvmOnly() {
        graalvmOnlyTest()
    }

    @Test
    fun `enclave class name change`() {
        assertTaskIsIncremental {
            assertThat(output).hasContent("com.test.enclave.TestEnclave")
            val enclaveCode = projectDir.resolve("src/main/kotlin/com/test/enclave/EnclaveTest.kt")
            enclaveCode.searchAndReplace("TestEnclave", "TestEnclave2")
        }
        assertThat(output).hasContent("com.test.enclave.TestEnclave2")
    }
}
