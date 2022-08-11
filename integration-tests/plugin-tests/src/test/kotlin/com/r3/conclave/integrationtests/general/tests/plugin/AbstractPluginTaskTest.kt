package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

abstract class AbstractPluginTaskTest(private val taskName: String, private val modeDependent: Boolean) {
    @TempDir
    @JvmField
    var projectDir: Path? = null

    @BeforeEach
    fun copyProject() {
        // Make a copy of the test project so that each test has a clean slate.
        val baseProjectDir = Path.of(this::class.java.classLoader.getResource("test-enclave")!!.toURI())
        Files.walkFileTree(baseProjectDir, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                projectDir!!.resolve(baseProjectDir.relativize(dir)).createDirectories()
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                file.copyTo(projectDir!!.resolve(baseProjectDir.relativize(file)))
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun runTask(): BuildTask {
        val name = if (modeDependent) "$taskName$enclaveMode" else taskName
        val runner = gradleRunner(name, projectDir!!)
        val buildResult = runner.build()
        return buildResult.task(":enclave:$name")!!
    }

    fun assertTaskRunIsIncremental() {
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    companion object {
        val enclaveMode = System.getProperty("enclaveMode").toLowerCase().capitalize()
        private val testGradleUserHome = System.getProperty("test.gradle.user.home")
        private val gradleVersion = System.getProperty("gradle.version")

        /**
         * GradleRunner is set withDebug(false) since it can cause tasks, such as the Copy task,
         * to misbehave and delete stale output directories after being considered UP_TO_DATE.
         */
        fun gradleRunner(task: String, projectDirectory: Path): GradleRunner {
            return GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withDebug(false)
                .withProjectDir(projectDirectory.toFile())
                .withArguments(task, "--no-build-cache", "--stacktrace", "--info", "--gradle-user-home", testGradleUserHome)
                .forwardOutput()
        }

        fun replaceAndRewriteBuildFile(projectDirectory: Path, oldValue: String, newValue: String) {
            val projectFile = projectDirectory.resolve("build.gradle")
            val newProjectFile = projectFile.readText().replace(oldValue, newValue)
            projectFile.writeText(newProjectFile)
        }
    }
}
