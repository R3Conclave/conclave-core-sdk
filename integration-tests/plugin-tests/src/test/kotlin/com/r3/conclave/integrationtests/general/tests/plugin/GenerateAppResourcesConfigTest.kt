package com.r3.conclave.integrationtests.general.tests.plugin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.integrationtests.general.commontest.TestUtils.graalvmOnlyTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class GenerateAppResourcesConfigTest : AbstractConclaveTaskTest() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun check() {
            graalvmOnlyTest()
        }
    }

    override val taskName: String get() = "generateAppResourcesConfig"
    override val outputName: String get() = "app-resources-config.json"

    @Test
    fun `task re-runs on addition of resource file`() {
        assertTaskIsIncremental {
            val resourceFile = projectDir.resolve("src/main/resources/test-resource.txt")
            resourceFile.parent.createDirectories()
            resourceFile.createFile()
        }
        assertThat(resourcesConfig()["includes"].map { it["pattern"].textValue() }).contains("test-resource.txt")
    }

    private fun resourcesConfig(): JsonNode = ObjectMapper().readTree(output.toFile())["resources"]
}
