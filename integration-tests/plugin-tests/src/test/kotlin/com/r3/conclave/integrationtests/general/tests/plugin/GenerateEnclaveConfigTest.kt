package com.r3.conclave.integrationtests.general.tests.plugin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAALVM
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.div

class GenerateEnclaveConfigTest : AbstractPluginTaskTest() {
    override val taskName: String get() = "generateEnclaveConfig${capitalisedEnclaveMode()}"
    override val output: Path get() = enclaveModeBuildDir / "enclave.xml"
    override val taskIsSpecificToRuntime get() = GRAALVM

    @Test
    fun productID() {
        assertTaskIsIncremental {
            assertThat(enxlaveConfig()["ProdID"].textValue()).isEqualTo("11")
            modifyProductIdConfig(111)
        }
        assertThat(enxlaveConfig()["ProdID"].textValue()).isEqualTo("111")
    }

    @Test
    fun revocationLevel() {
        assertTaskIsIncremental {
            assertThat(enxlaveConfig()["ISVSVN"].textValue()).isEqualTo("13")
            modifyRevocationLevelConfig(121)
        }
        assertThat(enxlaveConfig()["ISVSVN"].textValue()).isEqualTo("122")
    }

    @ParameterizedTest
    @CsvSource(
        "maxStackSize, StackMaxSize, 2097152, 16m, 16777216",
        "maxHeapSize, HeapMaxSize, 268435456, 2g, 2147483648",
    )
    fun `optional size bytes enclave config`(gradleConfig: String, xmlElement: String, defaultRawValue: Long,
                                             newBytesValue: String, newRawValue: Long) {
        assertThat(buildGradleFile).content().doesNotContain(gradleConfig)
        assertTaskIsIncremental {
            assertThat(enxlaveConfig()[xmlElement].textValue()).isEqualTo("0x${defaultRawValue.toString(16)}")
            addSimpleEnclaveConfig(gradleConfig, newBytesValue)
        }
        assertThat(enxlaveConfig()[xmlElement].textValue()).isEqualTo("0x${newRawValue.toString(16)}")
    }

    @Test
    fun maxThreads() {
        assertThat(buildGradleFile).content().doesNotContain("maxThreads")
        assertTaskIsIncremental {
            assertThat(enxlaveConfig()["TCSNum"].textValue()).isEqualTo("10")
            addSimpleEnclaveConfig("maxThreads", 15)
        }
        assertThat(enxlaveConfig()["TCSNum"].textValue()).isEqualTo("15")
    }

    private fun enxlaveConfig(): JsonNode = XmlMapper().readTree(output.toFile())
}
