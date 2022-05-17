package com.r3.conclave.integrationtests.general.tests.plugin

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GenerateEnclaveConfigTest : AbstractPluginTaskTest("generateEnclaveConfig", modeDependent = true) {
    private class EnclaveConfiguration {
        @JsonProperty("ProdID")
        val productID: Int? = null
        @JsonProperty("ISVSVN")
        val revocationLevel: Int? = null
        @JsonProperty("StackMaxSize")
        val maxStackSize: String? = null
        @JsonProperty("HeapMaxSize")
        val maxHeapSize: String? = null
        @JsonProperty("TCSNum")
        val tcsNum: Int? = null
        @JsonProperty("TCSPolicy")
        val tcsPolicy: Int? = null
        @JsonProperty("DisableDebug")
        val disableDebug: Int? = null
        @JsonProperty("MiscSelect")
        val miscSelect: Int? = null
        @JsonProperty ("MiscMask")
        val miscMask: String? = null
    }

    @Test
    fun `increment product id`() {
        assertTaskRunIsIncremental()

        val enclaveConfiguration = loadEnclaveConfigurationFromFile()

        val initialProductID = enclaveConfiguration.productID!!
        val expectedProductId = initialProductID + 1

        replaceAndRewriteBuildFile(
            projectDir!!,
            "productID = $initialProductID",
            "productID = $expectedProductId"
        )
        assertTaskRunIsIncremental()
        val updatedEnclaveConfiguration = loadEnclaveConfigurationFromFile()
        assertThat(updatedEnclaveConfiguration.productID).isEqualTo(expectedProductId)
    }

    @Test
    fun `increment revocation level`() {
        assertTaskRunIsIncremental()

        val enclaveConfiguration = loadEnclaveConfigurationFromFile()

        // The value on the file has been incremented by the task
        val initialRevocationLevel = enclaveConfiguration.revocationLevel!! - 1
        val expectedRevocationLevel = initialRevocationLevel + 1

        replaceAndRewriteBuildFile(
            projectDir!!,
            "revocationLevel = $initialRevocationLevel",
            "revocationLevel = $expectedRevocationLevel"
        )

        assertTaskRunIsIncremental()
        val updatedEnclaveConfiguration = loadEnclaveConfigurationFromFile()
        // The expected value on the file is the incremented value from the closure
        assertThat(updatedEnclaveConfiguration.revocationLevel).isEqualTo(expectedRevocationLevel + 1)
    }

    @Test
    fun `increment heap max size`() {
        assertTaskRunIsIncremental()

        val enclaveConfiguration = loadEnclaveConfigurationFromFile()
        val initialMaxHeapSize = enclaveConfiguration.maxHeapSize!!.drop(2).toLong(16)
        val expectedMaxHeapSize = initialMaxHeapSize + 1

        replaceAndRewriteBuildFile(
            projectDir!!,
            """maxHeapSize = "$initialMaxHeapSize"""",
            """maxHeapSize = "$expectedMaxHeapSize""""
        )

        assertTaskRunIsIncremental()
        val updatedEnclaveConfiguration = loadEnclaveConfigurationFromFile()
        assertThat(updatedEnclaveConfiguration.maxHeapSize!!.drop(2).toLong(16)).isEqualTo(expectedMaxHeapSize)
    }

    @Test
    fun `increment stack max size`() {
        assertTaskRunIsIncremental()

        val enclaveConfiguration = loadEnclaveConfigurationFromFile()
        val initialMaxStackSize = enclaveConfiguration.maxStackSize!!.drop(2).toLong(16)
        val expectedMaxStackSize = initialMaxStackSize + 1

        replaceAndRewriteBuildFile(
            projectDir!!,
            """maxStackSize = "$initialMaxStackSize"""",
            """maxStackSize = "$expectedMaxStackSize""""
        )

        assertTaskRunIsIncremental()
        val updatedEnclaveConfiguration = loadEnclaveConfigurationFromFile()
        assertThat(updatedEnclaveConfiguration.maxStackSize!!.drop(2).toLong(16)).isEqualTo(expectedMaxStackSize)
    }

    private fun loadEnclaveConfigurationFromFile(): EnclaveConfiguration {
        val enclaveConfigurationFile = projectDir!!.resolve("build/conclave/${enclaveMode.toLowerCase()}/enclave.xml")
        return XmlMapper().readValue(enclaveConfigurationFile.toFile(), EnclaveConfiguration::class.java)
    }
}
