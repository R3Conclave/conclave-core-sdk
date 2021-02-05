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
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class BuildUnsignedGraalEnclaveTest {
    companion object {
        private val projectDirectory : Path = GradleRunnerUtils.getProjectPath("build-unsigned-graal-enclave")
        private const val taskName = "buildUnsignedGraalEnclave"

        private fun runTask(buildType: BuildType): BuildTask? {
            val runner = gradleRunner("$taskName$buildType", projectDirectory)
            val buildResult = runner.build()
            return buildResult.task(":$taskName$buildType")
        }

        private fun assertLinkerScriptContent() {
            assertThat(File("$projectDirectory/build/conclave/Enclave.lds").readText()).isEqualTo(GenerateLinkerScript.content)
        }

        private fun writeConfigurationToFile(file: String, newConfiguration: String) {
            FileOutputStream(file).use { fos ->
                fos.write(newConfiguration.toByteArray())
            }
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
        assertLinkerScriptContent()
        task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertLinkerScriptContent()
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementMaxHeapSize(buildType: BuildType) {
        var task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertLinkerScriptContent()

        val initialMaxHeapSize = "268435456"
        val expectedMaxHeapSize = initialMaxHeapSize + 1

        ProjectUtils.replaceAndRewriteBuildFile(projectDirectory, "maxHeapSize = \"$initialMaxHeapSize\"", "maxHeapSize = \"$expectedMaxHeapSize\"")
        task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertLinkerScriptContent()
        // Revert changes for the next test
        ProjectUtils.replaceAndRewriteBuildFile(projectDirectory, "maxHeapSize = \"$expectedMaxHeapSize\"", "maxHeapSize = \"$initialMaxHeapSize\"")
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementMaxStackSize(buildType: BuildType) {
        var task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertLinkerScriptContent()

        val initialMaxStackSize = "2097152"
        val expectedMaxStackSize = initialMaxStackSize + 1

        ProjectUtils.replaceAndRewriteBuildFile(projectDirectory, "maxStackSize = \"$initialMaxStackSize\"", "maxStackSize = \"$expectedMaxStackSize\"")
        task = runTask(buildType)
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertLinkerScriptContent()
        // Revert changes for the next test
        ProjectUtils.replaceAndRewriteBuildFile(projectDirectory, "maxStackSize = \"$expectedMaxStackSize\"", "maxStackSize = \"$initialMaxStackSize\"")
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun reflectionConfigurationFilesIncrementalBuild(buildType: BuildType) {
        val originalConfigurations = mutableListOf<String>()
        for (i in 0..1) {
            val file = "$projectDirectory/reflectionconfig$i.json"
            val className = "Class${i}"
            val configuration = String(Files.readAllBytes(Paths.get(file)))
            originalConfigurations.add(configuration)

            val newConfiguration = configuration.replace(className, "Another$className")
            writeConfigurationToFile(file, newConfiguration)
            var task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }

        // Restore configurations
        for (i in 0..1) {
            val file = "$projectDirectory/reflectionconfig$i.json"
            writeConfigurationToFile(file, originalConfigurations[i])

            var task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }
}