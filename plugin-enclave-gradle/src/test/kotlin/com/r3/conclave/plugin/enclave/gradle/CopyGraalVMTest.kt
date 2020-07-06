package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.plugin.enclave.gradle.util.ChecksumUtils
import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CopyGraalVMTest {
    companion object {
        private val projectDirectory: Path = GradleRunnerUtils.getProjectPath("copy-graal")
        private val graalVMTarPath = "$projectDirectory/build/conclave/com/r3/conclave/graalvm/graalvm.tar"
        private const val taskName = "copyGraalVM"
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
        var graalTarFile = File(graalVMTarPath)
        assertThat(graalTarFile.length()).isPositive()

        val firstBuildChecksum = ChecksumUtils.sha512(graalVMTarPath)

        task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        graalTarFile = File(graalVMTarPath)
        assertThat(graalTarFile.length()).isPositive()

        val rebuildDigest = ChecksumUtils.sha512(graalVMTarPath)
        assertThat(firstBuildChecksum).isEqualTo(rebuildDigest)
    }

    @Test
    fun deletingOutputForcesTaskToRun() {
        var task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(graalVMTarPath).length()).isPositive()

        val firstBuildChecksum = ChecksumUtils.sha512(graalVMTarPath)

        Files.delete(Paths.get(graalVMTarPath))

        task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(graalVMTarPath).length()).isPositive()

        val rebuildDigest = ChecksumUtils.sha512(graalVMTarPath)
        assertThat(firstBuildChecksum).isEqualTo(rebuildDigest)
    }
}