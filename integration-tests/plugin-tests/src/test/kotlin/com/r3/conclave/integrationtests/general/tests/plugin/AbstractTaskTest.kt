package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.utilities.internal.UtilsKt.digest
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

abstract class AbstractTaskTest : TaskTest {
    @field:TempDir
    override lateinit var projectDir: Path
    open val runDeletionAndReproducibilityTest: Boolean get() = true

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
    fun `check task is incremental on output deletion and check reproducibility`() {
        assumeTrue(runDeletionAndReproducibilityTest)
        assertThat(output).doesNotExist()
        val originalHashes: Map<Path, ByteArray>? = assertTaskIsIncremental {
            assertThat(output).exists()
            val map = if (isReproducible) getAllOutputFiles().associateWith { digest(it, "SHA-256") } else null
            output.toFile().deleteRecursively()
            map
        }
        assertThat(output).exists()
        if (originalHashes != null) {
            val currentFiles = getAllOutputFiles()
            assertThat(currentFiles).describedAs("output files").containsOnlyOnceElementsOf(originalHashes.keys)
            for (file in currentFiles) {
                assertThat(file).describedAs("reproducibility of %s", file).hasDigest("SHA-256", originalHashes[file])
            }
        }
    }

    private fun getAllOutputFiles(): List<Path> {
        return Files.walk(output).use { paths ->
            paths.filter { it.isRegularFile() }.toList()
        }
    }

    fun changeToPythonEnclave(enclaveCode: String = """
            def on_enclave_startup:
                print("Python enclave started")
        """.trimIndent()
    ) {
        val srcMain = projectDir / "src" / "main"
        srcMain.toFile().deleteRecursively()
        val pythonScript = srcMain / "python" / "user-enclave.py"
        pythonScript.parent.createDirectories()
        pythonScript.writeText(enclaveCode)
    }

    fun updateGradleBuildFile(oldValue: String, newValue: String) {
        buildGradleFile.searchAndReplace(oldValue, newValue)
    }

    fun Path.searchAndReplace(oldValue: String, newValue: String) {
        val oldText = readText()
        val newText = oldText.replace(oldValue, newValue)
        require(newText != oldText) { "'$oldValue' does not exist in $this:\n$oldText" }
        writeText(newText)
    }

    fun runTask(taskName: String = this.taskName): BuildTask {
        val runner = gradleRunner(taskName, projectDir)
        val buildResult = runner.build()
        return buildResult.task(":$taskName")!!
    }

    fun runTaskAndAssertItsIncremental(taskName: String = this.taskName) {
        assertThat(runTask(taskName).outcome).describedAs("first run of %s", taskName).isEqualTo(SUCCESS)
        assertThat(runTask(taskName).outcome).describedAs("second run of %s", taskName).isEqualTo(UP_TO_DATE)
    }

    /**
     * Assert the Gradle task is incremental, namely that it does not run when its inputs haven't changed, and that
     * it runs again when they have.
     */
    fun <T> assertTaskIsIncremental(modify: () -> T): T {
        // First fresh run and then make sure the second run is up-to-date.
        runTaskAndAssertItsIncremental()
        val value = modify()
        // Then check that the build runs again with the new build.gradle changes and then is up-to-date again.
        runTaskAndAssertItsIncremental()
        return value
    }

    companion object {
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
                    "-PruntimeType=${TestUtils.runtimeType}",
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
