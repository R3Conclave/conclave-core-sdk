package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils
import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils.Companion.gradleRunner
import com.r3.conclave.plugin.enclave.gradle.util.ProjectUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Path

class BuildUnsignedEnclaveTest {
    companion object {
        private val projectDirectory : Path = GradleRunnerUtils.getProjectPath("build-unsigned-enclave")
        private const val taskName = "buildUnsignedEnclave"

        private fun runTask(buildType: BuildType): BuildTask? {
            val runner = gradleRunner("$taskName$buildType", projectDirectory)
            val buildResult = runner.build()
            return buildResult.task(":$taskName$buildType")
        }
    }

    @BeforeEach
    fun setup() {
        GradleRunnerUtils.clean(projectDirectory)
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementalBuild(buildType: BuildType) {
        var task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun modifyJVM(buildType: BuildType) {
        var task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        ProjectUtils.replaceAndRewriteBuildFile(projectDirectory,"runtime = avian", "runtime = graalvm_native_image")

        task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        ProjectUtils.replaceAndRewriteBuildFile(projectDirectory,"runtime = graalvm_native_image", "runtime = avian")

        task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}