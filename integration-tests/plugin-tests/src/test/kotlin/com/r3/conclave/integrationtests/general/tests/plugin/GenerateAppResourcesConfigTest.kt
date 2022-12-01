package com.r3.conclave.integrationtests.general.tests.plugin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.conclave.integrationtests.general.commontest.TestUtils.RuntimeType.GRAALVM
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div

class GenerateAppResourcesConfigTest : AbstractPluginTaskTest() {
    override val taskName: String get() = "generateAppResourcesConfig"
    override val output: Path get() = conclaveBuildDir / "app-resources-config.json"
    override val taskIsSpecificToRuntime get() = GRAALVM

    @Test
    fun `task re-runs on addition of resource file`() {
        assertTaskIsIncrementalUponInputChange {
            val resourceFile = projectDir.resolve("src/main/resources/test-resource.txt")
            resourceFile.parent.createDirectories()
            resourceFile.createFile()
        }
        assertThat(resourcesConfig()["includes"].map { it["pattern"].textValue() }).contains("test-resource.txt")
    }

    private fun resourcesConfig(): JsonNode = ObjectMapper().readTree(output.toFile())["resources"]
}
