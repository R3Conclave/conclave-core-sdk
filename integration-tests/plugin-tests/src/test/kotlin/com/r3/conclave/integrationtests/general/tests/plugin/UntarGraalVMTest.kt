package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.streams.toList

class UntarGraalVMTest : AbstractPluginTaskTest("untarGraalVM", modeDependent = false) {
    private val graalVMPath get() = "$projectDir/build/conclave/com/r3/conclave/graalvm"
    private val graalVMDistributionPath get() = Path.of("$graalVMPath/distribution")

    @Test
    fun `deleting input does not run task`() {
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
        Path.of("$graalVMPath/graalvm.tar").deleteExisting()
        // The regenerated input is the same so this task doesn't run
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `deleting random output forces task to run`() {
        assertTaskRunIsIncremental()

        val outputFiles = Files.walk(graalVMDistributionPath).use { it.filter(Files::isRegularFile).toList() }
        val fileToDelete = outputFiles.random()
        println("Deleting $fileToDelete")
        fileToDelete.deleteExisting()

        assertTaskRunIsIncremental()
    }
}
