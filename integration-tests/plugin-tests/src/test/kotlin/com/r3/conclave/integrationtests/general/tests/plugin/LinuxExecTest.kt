package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class LinuxExecTest : AbstractPluginTaskTest("setupLinuxExecEnvironment", modeDependent = false) {
    private val dockerFilePath get() = Path.of("$projectDir/build/conclave/com/r3/conclave/docker/Dockerfile")

    @Test
    fun `incremental build`() {
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
        val dockerFileContents = dockerFilePath.readText()
        // For subsequent increment builds we still want to execute the
        // docker build - this is because the docker image lives outside the source
        // tree and the developer may have deleted it independently of our build.
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(dockerFilePath).hasContent(dockerFileContents)
    }
}
