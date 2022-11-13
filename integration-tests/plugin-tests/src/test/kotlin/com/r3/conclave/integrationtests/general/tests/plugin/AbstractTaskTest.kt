package com.r3.conclave.integrationtests.general.tests.plugin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

abstract class AbstractTaskTest {
    abstract val taskName: String
    abstract val output: Path
    /**
     * If the task's output isn't stable then override with false, and explain why.
     */
    open val isReproducible: Boolean get() = true

    @field:TempDir
    lateinit var projectDir: Path

    val projectName: String get() = projectDir.name
    val buildFile: Path get() = projectDir / "build.gradle"
    val buildDir: Path get() = projectDir / "build"

    @BeforeEach
    fun copyProject() {
        // Make a copy of the test project so that each test has a clean slate.
        val baseProjectDir = Path.of(this::class.java.classLoader.getResource("test-enclave")!!.toURI())
        Files.walkFileTree(baseProjectDir, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                projectDir.resolve(baseProjectDir.relativize(dir)).createDirectories()
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                file.copyTo(projectDir.resolve(baseProjectDir.relativize(file)))
                return FileVisitResult.CONTINUE
            }
        })
    }

    @Test
    fun `deleting output forces task to re-run`() {
        val originalContent = assertTaskIsIncremental {
            assertThat(output).exists()
            if (isReproducible && output.isRegularFile()) {
                val originalContent = output.readBytes()
                output.deleteExisting()
                originalContent
            } else {
                output.toFile().deleteRecursively()
                null
            }
        }
        assertThat(output).exists()
        if (originalContent != null) {
            assertThat(output).hasBinaryContent(originalContent)
        }
    }

    fun updateBuildFile(oldValue: String, newValue: String) {
        buildFile.searchAndReplace(oldValue, newValue)
    }

    fun Path.searchAndReplace(oldValue: String, newValue: String) {
        val oldText = readText()
        val newText = oldText.replace(oldValue, newValue)
        require(newText != oldText) { "'$oldValue' does not exist in $this" }
        writeText(newText)
    }

    fun runTask(): BuildTask {
        val runner = gradleRunner(taskName, projectDir)
        val buildResult = runner.build()
        return buildResult.task(":$taskName")!!
    }

    fun assertTaskRunIsIncremental() {
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(runTask().outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    /**
     * Assert the Gradle task is incremental, namely that it does not run when its inputs haven't changed, and that
     * it runs again when they have.
     */
    fun <T> assertTaskIsIncremental(modify: () -> T): T {
        // First fresh run and then make sure the second run is up-to-date.
        assertTaskRunIsIncremental()
        val value = modify()
        // Then check that the build runs again with the new build.gradle changes and then is up-to-date again.
        assertTaskRunIsIncremental()
        return value
    }

    companion object {
        val enclaveMode = System.getProperty("enclaveMode").lowercase().replaceFirstChar(Char::titlecase)
        private val runtimeType = System.getProperty("runtimeType")
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
                .withArguments(
                    task,
                    "-PruntimeType=$runtimeType",
                    "--no-build-cache",
                    "--stacktrace",
                    "--info",
                    "--gradle-user-home",
                    testGradleUserHome
                )
                .forwardOutput()
        }
    }
}
