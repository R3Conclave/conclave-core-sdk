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

class LinuxExecTest {
    companion object {
        private val projectDirectory: Path = GradleRunnerUtils.getProjectPath("linux-exec")
        private val dockerFilePath = "$projectDirectory/build/conclave/com/r3/conclave/docker/Dockerfile"
        private const val taskName = "setupLinuxExecEnvironment"
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
        var dockerFile = File(dockerFilePath)
        assertThat(dockerFile.length()).isPositive()

        val firstBuildChecksum = ChecksumUtils.sha512(dockerFilePath)

        // For subsequent increment builds we still want to execute the 
        // docker build - this is because the docker image lives outside the source
        // tree and the developer may have deleted it independently of our build.
        task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        dockerFile = File(dockerFilePath)
        assertThat(dockerFile.length()).isPositive()

        val rebuildDigest = ChecksumUtils.sha512(dockerFilePath)
        assertThat(firstBuildChecksum).isEqualTo(rebuildDigest)
    }

    @Test
    fun deletingOutputForcesTaskToRun() {
        var task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(dockerFilePath).length()).isPositive()

        val firstBuildChecksum = ChecksumUtils.sha512(dockerFilePath)

        Files.delete(Paths.get(dockerFilePath))

        task = gradleRunner.build().task(":$taskName")
        assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(dockerFilePath).length()).isPositive()

        val rebuildDigest = ChecksumUtils.sha512(dockerFilePath)
        assertThat(firstBuildChecksum).isEqualTo(rebuildDigest)
    }
}