package com.r3.conclave.plugin.enclave.gradle

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class GenerateEnclaveConfigTest {

    companion object {
        private val testGradleUserHome : String = System.getProperty("test.gradle.user.home")
        private val gradleVersion : String = System.getProperty("gradle.version")
        private val projectDirectory : Path = Paths.get(GenerateEnclaveConfigTest::class.java.classLoader.getResource("generate-enclave-configuration")!!.toURI())
        private val xmlMapper = XmlMapper()

        private fun gradleRunner(task: String): GradleRunner {
            return GradleRunner.create()
                    .withGradleVersion(gradleVersion)
                    .withDebug(true)
                    .withProjectDir(projectDirectory.toFile())
                    .withArguments(task, "--no-build-cache", "--stacktrace", "--info", "--gradle-user-home", testGradleUserHome)
        }

        private fun runAndAssertTask(buildType: BuildType) {
            val runner = gradleRunner("generateEnclaveConfig${buildType.name}")
            val buildResult = runner.build()
            val task = buildResult.task(":generateEnclaveConfig${buildType.name}")
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        private fun loadEnclaveConfigurationFromFile(buildType: BuildType): EnclaveConfiguration {
            val enclaveConfigurationFile = projectDirectory.resolve("build/conclave/${buildType.name.toLowerCase()}/enclave.xml")
            return xmlMapper.readValue(enclaveConfigurationFile.toFile(), EnclaveConfiguration::class.java)
        }

        private fun replaceAndRewriteBuildFile(oldValue: String, newValue: String) {
            val projectFile = projectDirectory.resolve("build.gradle").toFile()
            val newProjectFile = projectFile.readText().replace(oldValue, newValue)
            Files.write(projectFile.toPath(), newProjectFile.toByteArray())
        }
    }

    class EnclaveConfiguration {
        @JsonProperty("ProdID")
        val productID: Int? = null
        @JsonProperty("ISVSVN")
        val revocationLevel: Int? = null
        @JsonProperty("StackMaxSize")
        val maxStackSize: String? = null
        @JsonProperty("HeapMaxSize")
        val maxHeapSize: String? = null
        @JsonProperty("TCSNum")
        val tcsNum: Int? = null
        @JsonProperty("TCSPolicy")
        val tcsPolicy: Int? = null
        @JsonProperty("DisableDebug")
        val disableDebug: Int? = null
        @JsonProperty("MiscSelect")
        val miscSelect: Int? = null
        @JsonProperty ("MiscMask")
        val miscMask: String? = null
    }

    @BeforeEach
    fun setupTest() {
        File("$projectDirectory/.gradle").deleteRecursively()
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementProductID(buildType: BuildType) {
        runAndAssertTask(buildType)

        val enclaveConfiguration = loadEnclaveConfigurationFromFile(buildType)

        val initialProductID = enclaveConfiguration.productID!!
        val expectedProductId = initialProductID + 1

        replaceAndRewriteBuildFile("productID = $initialProductID", "productID = $expectedProductId")
        runAndAssertTask(buildType)
        val updatedEnclaveConfiguration = loadEnclaveConfigurationFromFile(buildType)
        assertThat(updatedEnclaveConfiguration.productID).isEqualTo(expectedProductId)
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementRevocationLevel(buildType: BuildType) {
        runAndAssertTask(buildType)

        val enclaveConfiguration = loadEnclaveConfigurationFromFile(buildType)

        // The value on the file has been incremented by the task
        val initialRevocationLevel = enclaveConfiguration.revocationLevel!! - 1
        val expectedRevocationLevel = initialRevocationLevel + 1

        replaceAndRewriteBuildFile("revocationLevel = $initialRevocationLevel", "revocationLevel = $expectedRevocationLevel")

        runAndAssertTask(buildType)
        val updatedEnclaveConfiguration = loadEnclaveConfigurationFromFile(buildType)
        // The expected value on the file is the incremented value from the closure
        assertThat(updatedEnclaveConfiguration.revocationLevel).isEqualTo(expectedRevocationLevel + 1)
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementHeapMaxSize(buildType: BuildType) {
        runAndAssertTask(buildType)

        val enclaveConfiguration = loadEnclaveConfigurationFromFile(buildType)
        val initialMaxHeapSize = enclaveConfiguration.maxHeapSize!!.drop(2).toLong(16)
        val expectedMaxHeapSize = initialMaxHeapSize + 1

        replaceAndRewriteBuildFile("maxHeapSize = \"$initialMaxHeapSize\"", "maxHeapSize = \"$expectedMaxHeapSize\"")

        runAndAssertTask(buildType)
        val updatedEnclaveConfiguration = loadEnclaveConfigurationFromFile(buildType)
        assertThat(updatedEnclaveConfiguration.maxHeapSize!!.drop(2).toLong(16)).isEqualTo(expectedMaxHeapSize)
    }
}
