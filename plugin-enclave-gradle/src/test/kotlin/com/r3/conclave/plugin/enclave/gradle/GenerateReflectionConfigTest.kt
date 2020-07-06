package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

class GenerateReflectionConfigTest {
    companion object {
        private val projectDirectory: Path = GradleRunnerUtils.getProjectPath("generate-reflection-config")
        private val reflectConfigPath = "${projectDirectory}/build/conclave/reflectconfig"
        private const val taskName = "generateReflectionConfig"
    }

    private val gradleRunner = GradleRunnerUtils.gradleRunner(taskName, projectDirectory)

    @BeforeEach
    fun setup() {
        GradleRunnerUtils.clean(projectDirectory)
    }

    @Test
    fun incrementalBuild() {
        var task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(reflectConfigPath).readBytes()).isEqualTo(GenerateReflectionConfig.generateContent(listOf("com.r3.conclave.plugin.enclave.gradle.test.TestEnclave")).toByteArray())

        task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(File(reflectConfigPath).readBytes()).isEqualTo(GenerateReflectionConfig.generateContent(listOf("com.r3.conclave.plugin.enclave.gradle.test.TestEnclave")).toByteArray())
    }

    @Test
    fun deletingOutputForcesTaskToRun() {
        var task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val file = File(reflectConfigPath)
        assertThat(file.readBytes()).isEqualTo(GenerateReflectionConfig.generateContent(listOf("com.r3.conclave.plugin.enclave.gradle.test.TestEnclave")).toByteArray())

        assertTrue(file.delete())

        task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(reflectConfigPath).readBytes()).isEqualTo(GenerateReflectionConfig.generateContent(listOf("com.r3.conclave.plugin.enclave.gradle.test.TestEnclave")).toByteArray())
    }
}