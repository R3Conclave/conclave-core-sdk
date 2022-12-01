package com.r3.conclave.integrationtests.general.tests.plugin

import com.r3.conclave.integrationtests.general.commontest.TestUtils
import com.r3.conclave.integrationtests.general.commontest.TestUtils.enclaveMode
import com.r3.conclave.integrationtests.general.commontest.TestUtils.runtimeType
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
import java.security.interfaces.RSAPublicKey
import kotlin.io.path.*

/**
 * Extend this class to test a task from the enclave Gradle plugin. By default the [check task is incremental on output deletion and check reproducibility]
 * test will be run which makes sure the task is "incremental" and produces a stable output. A Gradle task is
 * incremental if it only runs when at least one of its inputs has changed, and doesn't run if none of them have. In
 * the context of the enclave plugin the input is the enclave config, represented by the `conclave { ... }` block in
 * the build.gradle file.
 */
abstract class AbstractPluginTaskTest {
    @field:TempDir
    lateinit var projectDir: Path

    /**
     * The name of task to test.
     */
    abstract val taskName: String

    /**
     * The file or directory output of the task. Use [buildDir], [conclaveBuildDir] or [enclaveModeBuildDir] to help
     * define this location.
     */
    abstract val output: Path

    /**
     * The name of the enclave project being used to test the task.
     */
    val projectName: String get() = projectDir.name

    /**
     * Path to the enclave build.gradle file.
     */
    val buildGradleFile: Path get() = projectDir / "build.gradle"

    /**
     * The enclave project's build directory.
     */
    val buildDir: Path get() = projectDir / "build"

    /**
     * The "conclave" sub-directory in the enclave build directory.
     */
    val conclaveBuildDir: Path get() = buildDir / "conclave"

    /**
     * The enclave mode specific sub-directory for the current mode.
     */
    val enclaveModeBuildDir: Path get() = conclaveBuildDir / enclaveMode.name.lowercase()

    /**
     * The path of the dummy key file.
     */
    val dummyKeyFile: Path get() = conclaveBuildDir / "dummy_key.pem"

    /**
     * Override if the task only runs for a specific runtime.
     */
    open val taskIsSpecificToRuntime: TestUtils.RuntimeType? get() = null

    /**
     * Override if the task only runs in a specific enclave mode.
     */
    open val taskIsSpecificToMode: TestUtils.ITEnclaveMode? get() = null

    /**
     * If the task's output isn't stable then override with false, and explain why.
     */
    open val isReproducible: Boolean get() = true

    open val runDeletionAndReproducibilityTest: Boolean get() = true

    @BeforeEach
    fun checkPreconditions() {
        taskIsSpecificToRuntime?.let { assumeTrue(it == runtimeType) }
        taskIsSpecificToMode?.let { assumeTrue(it == enclaveMode) }
    }

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
        val originalHashes: Map<Path, ByteArray>? = runTaskAfterInputChangeAndAssertItsIncremental {
            assertThat(output).exists()
            val map = if (isReproducible) getAllOutputFiles().associateWith { digest(it, "SHA-256") } else null
            output.toFile().deleteRecursively()
            map
        }
        assertThat(output).describedAs("output %s recreated", output).exists()
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

    /**
     * Modify the `productID` config in the `conclave` block.
     */
    fun modifyProductIdConfig(newValue: Int) {
        modifyGradleBuildFile("productID = 11", "productID = $newValue")
    }

    /**
     * Modify the `revocationLevel` config in the `conclave` block.
     */
    fun modifyRevocationLevelConfig(newValue: Int) {
        modifyGradleBuildFile("revocationLevel = 12", "revocationLevel = $newValue")
    }

    /**
     * Add a new simple config in the `conclave` block.
     */
    fun addSimpleEnclaveConfig(name: String, value: Any) {
        val valueString = if (value is String) "\"$value\"" else value.toString()
        addEnclaveConfigBlock("$name = $valueString")
    }

    /**
     * Add config into the enclave mode section of the `conclave` block. For example, if the current mode is debug,
     * then this will insert the given config string into:
     * ```
     * conclave {
     *     debug {
     *        ...
     *     }
     * }
     * ```
     */
    fun addEnclaveModeConfig(newConfig: String) {
        addEnclaveConfigBlock(
            """${enclaveMode.name.lowercase()} {
                    |   $newConfig
                    |}
                    |""".trimMargin()
        )
    }

    /**
     * Add the given config string into the `conclave` block.
     */
    fun addEnclaveConfigBlock(newConfig: String) {
        modifyGradleBuildFile("conclave {\n", "conclave {\n$newConfig\n")
    }

    /**
     * Modify the enclave build.gradle by replacing every occurrence of [oldValue] with [newValue].
     */
    fun modifyGradleBuildFile(oldValue: String, newValue: String) {
        buildGradleFile.searchAndReplace(oldValue, newValue)
    }

    fun Path.searchAndReplace(oldValue: String, newValue: String) {
        require(oldValue != newValue) { "Old and new values must be different: $oldValue" }
        val oldText = readText()
        val newText = oldText.replace(oldValue, newValue)
        require(newText != oldText) { "'$oldValue' does not exist in $this:\n$oldText" }
        writeText(newText)
    }

    fun changeToPythonEnclave(
        enclaveCode: String = """
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
     * Assert the Gradle task is incremental after the given modification.
     */
    fun <T> runTaskAfterInputChangeAndAssertItsIncremental(modification: () -> T): T {
        // First fresh run and then make sure the second run is up-to-date.
        runTaskAndAssertItsIncremental()
        val value = modification()
        // Then check that the build runs again with the new build.gradle changes and then is up-to-date again.
        runTaskAndAssertItsIncremental()
        return value
    }

    /**
     * Read the dummy RSA public key, if it exists.
     */
    fun dummyKey(): RSAPublicKey = TestUtils.readSigningKey(dummyKeyFile)

    fun capitalisedEnclaveMode(): String = enclaveMode.name.lowercase().replaceFirstChar(Char::titlecase)

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
