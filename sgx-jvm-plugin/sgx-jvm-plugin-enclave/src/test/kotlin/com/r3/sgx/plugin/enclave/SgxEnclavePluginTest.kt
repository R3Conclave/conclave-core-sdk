package com.r3.sgx.plugin.enclave

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.Test
import java.io.File
import kotlin.test.fail

class SgxEnclavePluginTest {
    private companion object {
        private val testGradleUserHome = System.getProperty("test.gradle.user.home") ?: fail("test.gradle.user.home is not set.")
    }

    @Test
    fun `project with no enclave class`() {
        val projectDirectory = getProjectDirectory("test-project-non-enclave")
        val runner = runSignedEnclaveSimulationJarTask(projectDirectory)
        val buildResult = runner.buildAndFail()
        println(buildResult.output)
        assertThat("There are no classes that extend com.r3.conclave.enclave.Enclave" in buildResult.output).isTrue()
    }

    private fun getProjectDirectory(projectDirectory: String) = File(javaClass.classLoader.getResource(projectDirectory)!!.toURI())

    private fun runSignedEnclaveSimulationJarTask(projectDirectory: File): GradleRunner {
        return GradleRunner
                .create()
                .withDebug(true)
                .withProjectDir(projectDirectory)
                .withArguments("signedEnclaveSimulationJar", "--stacktrace", "--info", "--gradle-user-home", testGradleUserHome)
    }
}
