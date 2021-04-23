package com.r3.conclave.plugin.enclave.gradle

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils
import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils.Companion.gradleRunner
import com.r3.conclave.plugin.enclave.gradle.util.ProjectUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class GenerateEnclaveConfigTest {

    companion object {
        private val projectDirectory : Path = GradleRunnerUtils.getProjectPath("generate-enclave-configuration")

        private fun runAndAssertTask(buildType: BuildType) {
            val runner = gradleRunner("generateEnclaveConfig${buildType.name}", projectDirectory)
            val buildResult = runner.build()
            val task = buildResult.task(":generateEnclaveConfig${buildType.name}")
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
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
        GradleRunnerUtils.clean(projectDirectory)
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementProductID(buildType: BuildType) {
        if (buildType != BuildType.Mock) {
            runAndAssertTask(buildType)

            val enclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)

            val initialProductID = enclaveConfiguration.productID!!
            val expectedProductId = initialProductID + 1

            ProjectUtils.replaceAndRewriteBuildFile(projectDirectory,
                    "productID = $initialProductID",
                    "productID = $expectedProductId")
            runAndAssertTask(buildType)
            val updatedEnclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)
            assertThat(updatedEnclaveConfiguration.productID).isEqualTo(expectedProductId)
        }
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementRevocationLevel(buildType: BuildType) {
        if (buildType != BuildType.Mock) {
            runAndAssertTask(buildType)

            val enclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)

            // The value on the file has been incremented by the task
            val initialRevocationLevel = enclaveConfiguration.revocationLevel!! - 1
            val expectedRevocationLevel = initialRevocationLevel + 1

            ProjectUtils.replaceAndRewriteBuildFile(projectDirectory, "revocationLevel = $initialRevocationLevel", "revocationLevel = $expectedRevocationLevel")

            runAndAssertTask(buildType)
            val updatedEnclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)
            // The expected value on the file is the incremented value from the closure
            assertThat(updatedEnclaveConfiguration.revocationLevel).isEqualTo(expectedRevocationLevel + 1)
        }
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementHeapMaxSize(buildType: BuildType) {
        if (buildType != BuildType.Mock) {
            runAndAssertTask(buildType)

            val enclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)
            val initialMaxHeapSize = enclaveConfiguration.maxHeapSize!!.drop(2).toLong(16)
            val expectedMaxHeapSize = initialMaxHeapSize + 1

            ProjectUtils.replaceAndRewriteBuildFile(projectDirectory, "maxHeapSize = \"$initialMaxHeapSize\"", "maxHeapSize = \"$expectedMaxHeapSize\"")

            runAndAssertTask(buildType)
            val updatedEnclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)
            assertThat(updatedEnclaveConfiguration.maxHeapSize!!.drop(2).toLong(16)).isEqualTo(expectedMaxHeapSize)
        }
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementStackMaxSize(buildType: BuildType) {
        if (buildType != BuildType.Mock) {
            runAndAssertTask(buildType)

            val enclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)
            val initialMaxStackSize = enclaveConfiguration.maxStackSize!!.drop(2).toLong(16)
            val expectedMaxStackSize = initialMaxStackSize + 1

            ProjectUtils.replaceAndRewriteBuildFile(projectDirectory, "maxStackSize = \"$initialMaxStackSize\"", "maxStackSize = \"$expectedMaxStackSize\"")

            runAndAssertTask(buildType)
            val updatedEnclaveConfiguration = ProjectUtils.loadEnclaveConfigurationFromFile(projectDirectory, buildType)
            assertThat(updatedEnclaveConfiguration.maxStackSize!!.drop(2).toLong(16)).isEqualTo(expectedMaxStackSize)
        }
    }
}
