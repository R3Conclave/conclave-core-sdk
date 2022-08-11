package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.deleteExisting

class GenerateReflectionConfigTest : AbstractPluginTaskTest("generateReflectionConfig", modeDependent = false) {
    private val reflectConfigPath get() = "$projectDir/enclave/build/conclave/reflectconfig"

    @Test
    fun `incremental build`() {
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(Path.of(reflectConfigPath)).content().contains("com.test.enclave.TestEnclave")
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `deleting output forces task to re-run`() {
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
        Path.of(reflectConfigPath).deleteExisting()
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
