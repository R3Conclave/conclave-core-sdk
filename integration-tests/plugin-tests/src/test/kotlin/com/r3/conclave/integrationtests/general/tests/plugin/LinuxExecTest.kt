package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class LinuxExecTest : AbstractPluginTaskTest("setupLinuxExecEnvironment", modeDependent = false) {
    @Test
    fun `incremental build`() {
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
