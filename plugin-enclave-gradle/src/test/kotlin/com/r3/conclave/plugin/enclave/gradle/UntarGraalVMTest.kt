package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class UntarGraalVMTest {
    companion object {
        private val projectDirectory : Path = GradleRunnerUtils.getProjectPath("untar-graal")
        private val graalVMPath = "$projectDirectory/build/conclave/com/r3/conclave/graalvm"
        private val graalVMDistributionPath = "$graalVMPath/distribution"

        private fun runTask(): BuildTask? {
            val runner = GradleRunnerUtils.gradleRunner("untarGraalVM", projectDirectory)
            val buildResult = runner.build()
            return buildResult.task(":untarGraalVM")
        }
    }

    @BeforeEach
    fun setup() {
        GradleRunnerUtils.clean(projectDirectory)
    }

    @Test
    fun incrementalBuild() {
        var task = runTask()
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(graalVMDistributionPath).listFiles()!!.size).isPositive()

        task = runTask()
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(File(graalVMDistributionPath).listFiles()!!.size).isPositive()
    }

    @Test
    fun deletingInputDoesNotRunTask() {
        var task = runTask()
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(graalVMPath).listFiles()!!.size).isPositive()

        Files.delete(Paths.get("$graalVMPath/graalvm.tar"))

        task = runTask()
        // The regenerated input is the same so this task doesn't run
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(File(graalVMPath).listFiles()!!.size).isPositive()
    }

    @Test
    fun deletingRandomOutputForcesTaskToRun() {
        var task = runTask()
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(graalVMDistributionPath).listFiles()!!.size).isPositive()

        val randomFile = File(graalVMDistributionPath).listFiles()!!.random()
        assertTrue(randomFile.deleteRecursively())

        task = runTask()
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(graalVMDistributionPath).listFiles()!!.size).isPositive()
    }
}