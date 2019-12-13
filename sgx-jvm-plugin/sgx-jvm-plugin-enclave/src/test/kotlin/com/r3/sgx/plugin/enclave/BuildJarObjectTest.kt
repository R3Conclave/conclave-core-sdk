package com.r3.sgx.plugin.enclave

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import java.io.File
import kotlin.test.fail

class BuildJarObjectTest {

    private companion object {
        private val testGradleUserHome = System.getProperty("test.gradle.user.home") ?: fail("test.gradle.user.home is not set.")
    }

    @Test
    fun enclaveSanityCheck() {
        val projectDirectory = getProjectDirectory("test-project-enclave")
        val runner = gradleRunner(projectDirectory)
        val buildResult = runner.build()
        val output = buildResult.output
        println(output)
        assertThat(buildResult.task(":shadowJarObject")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun nonEnclaveSanityCheck() {
        val projectDirectory = File(javaClass.classLoader.getResource("test-project-non-enclave").toURI())
        val runner = gradleRunner(projectDirectory)
        val buildResult = runner.buildAndFail()
        val output = buildResult.output
        println(output)
        assertThat(buildResult.task(":shadowJarObject")!!.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    private fun getProjectDirectory(projectDirectory: String) = File(javaClass.classLoader.getResource(projectDirectory).toURI())

    private fun gradleRunner(projectDirectory: File): GradleRunner {
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(projectDirectory)
                .withArguments("shadowJarObject", "--stacktrace", "--info", "--gradle-user-home", testGradleUserHome)
    }
}
