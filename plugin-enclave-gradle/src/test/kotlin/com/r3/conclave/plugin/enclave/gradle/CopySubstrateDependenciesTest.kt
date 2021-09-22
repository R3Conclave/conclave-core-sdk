package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.plugin.enclave.gradle.util.ChecksumUtils
import com.r3.conclave.plugin.enclave.gradle.util.GradleRunnerUtils
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.nio.file.Path

class CopySubstrateDependenciesTest {
    companion object {
        private val projectDirectory : Path = GradleRunnerUtils.getProjectPath("copy-substrate-dependencies")
        private val conclaveDependenciesPath = "$projectDirectory/build/conclave/com/r3/conclave"
        private val substrateDependenciesPath = "$conclaveDependenciesPath/substratevm"
        private val sgxDependenciesPath = "$conclaveDependenciesPath/sgx"
        private const val taskName = "copySubstrateDependencies"


        private fun runTask(buildType: BuildType): BuildTask? {
            val runner = GradleRunnerUtils.gradleRunner("$taskName$buildType", projectDirectory)
            val buildResult = runner.build()
            return buildResult.task(":$taskName$buildType")
        }

        private fun dependenciesFiles(buildType: BuildType): Array<File> {
            val simPrefix = if (buildType == BuildType.Simulation) "_sim" else ""
            val filesList = mutableListOf(
                    File("$substrateDependenciesPath/$buildType/libsubstratevm.a"),
                    File("$substrateDependenciesPath/$buildType/libfatfs_enclave.a"),
                    File("$substrateDependenciesPath/$buildType/libjvm_enclave_edl.a"),
                    File("$substrateDependenciesPath/$buildType/libjvm_enclave_common.a"),
                    File("$substrateDependenciesPath/$buildType/libjvm_host_enclave_common_enclave.a"),
                    File("$substrateDependenciesPath/$buildType/libz.a"),
                    File("$sgxDependenciesPath/$buildType/libcxx"),
                    File("$sgxDependenciesPath/$buildType/tlibc"),
                    File("$sgxDependenciesPath/$buildType/libsgx_pthread.a"),
                    File("$sgxDependenciesPath/$buildType/libsgx_tcrypto.a"),
                    File("$sgxDependenciesPath/$buildType/libsgx_tcxx.a"),
                    File("$sgxDependenciesPath/$buildType/libsgx_trts$simPrefix.a"),
                    File("$sgxDependenciesPath/$buildType/libsgx_tservice$simPrefix.a"),
                    File("$sgxDependenciesPath/$buildType/libsgx_tstdc.a")
            )
            return filesList.toTypedArray()
        }
    }

    @BeforeEach
    fun setup() {
        GradleRunnerUtils.clean(projectDirectory)
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun incrementalBuild(buildType: BuildType) {
        if (buildType != BuildType.Mock) {
            var task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val expectedDependenciesFiles = dependenciesFiles(buildType)
            val actualDependenciesFiles = File("$substrateDependenciesPath/$buildType").listFiles()!! + File("$sgxDependenciesPath/$buildType").listFiles()!!
            assertThat(actualDependenciesFiles).containsExactlyInAnyOrder(*expectedDependenciesFiles)

            val firstBuildChecksums = ChecksumUtils.sha512Directory(conclaveDependenciesPath)

            task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            val actualRebuildDependenciesFiles = File("$substrateDependenciesPath/$buildType").listFiles()!! + File("$sgxDependenciesPath/$buildType").listFiles()!!
            assertThat(actualRebuildDependenciesFiles).containsExactlyInAnyOrder(*expectedDependenciesFiles)

            val rebuildChecksums = ChecksumUtils.sha512Directory(conclaveDependenciesPath)
            assertThat(firstBuildChecksums).containsExactlyInAnyOrder(*rebuildChecksums.toTypedArray())
        }
    }

    @EnumSource(BuildType::class)
    @ParameterizedTest(name = "{index} => {0}")
    fun deletingRandomOutputForcesTaskToRun(buildType: BuildType) {
        if (buildType != BuildType.Mock) {
            var task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val expectedDependenciesFiles = dependenciesFiles(buildType)
            val actualDependenciesFiles = File("$substrateDependenciesPath/$buildType").listFiles()!! + File("$sgxDependenciesPath/$buildType").listFiles()!!
            assertThat(actualDependenciesFiles).containsExactlyInAnyOrder(*expectedDependenciesFiles)

            assertTrue(actualDependenciesFiles.random().deleteRecursively())

            task = runTask(buildType)
            assertThat(task!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val actualRebuildDependenciesFiles = File("$substrateDependenciesPath/$buildType").listFiles()!! + File("$sgxDependenciesPath/$buildType").listFiles()!!
            assertThat(actualRebuildDependenciesFiles).containsExactlyInAnyOrder(*expectedDependenciesFiles)
        }
    }
}
