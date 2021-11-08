package com.r3.conclave.init

import com.r3.conclave.init.common.deleteRecursively
import com.r3.conclave.init.common.walkTopDown
import com.r3.conclave.init.template.JavaClass
import com.r3.conclave.init.template.JavaPackage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.*

internal class TestCreateProject {
    lateinit var outputDir: Path

    @BeforeEach
    fun createOutput() {
        outputDir = createTempDirectory("conclave-project")
    }

    @AfterEach
    fun cleanUp() {
        outputDir.deleteRecursively()
    }

    @Test
    fun `test conclave init kotlin`() {
        testCreateProject(
            Language.KOTLIN,
            enclaveTestFilePath = "enclave/src/test/kotlin/com/megacorp/enclave/MegaEnclaveTest.kt",
            testFileStrings = listOf(
                "package com.megacorp.enclave\n",
                "class MegaEnclaveTest {",
                "val mockHost = EnclaveHost.load(\"com.megacorp.enclave.MegaEnclave\")"
            ),
            expectedExtension = "kt",
            invalidExtension = "java"
        )
    }


    @Test
    fun `test conclave init java`() {
        testCreateProject(
            language = Language.JAVA,
            enclaveTestFilePath = "enclave/src/test/java/com/megacorp/enclave/MegaEnclaveTest.java",
            testFileStrings = listOf(
                "package com.megacorp.enclave;\n",
                "class MegaEnclaveTest {",
                "EnclaveHost mockHost = EnclaveHost.load(\"com.megacorp.enclave.MegaEnclave\");"
            ),
            expectedExtension = "java",
            invalidExtension = "kt"
        )
    }

    private fun testCreateProject(
        language: Language,
        enclaveTestFilePath: String,
        testFileStrings: List<String>,
        expectedExtension: String,
        invalidExtension: String,
    ) {
        val basePackage = JavaPackage("com.megacorp")
        val enclaveClass = JavaClass("MegaEnclave")

        val conclaveRepo = createTempDirectory().apply { resolve("repo").createDirectories() }

        ConclaveInit.createProject(language, basePackage, enclaveClass, outputDir, conclaveRepo, "version-string")

        val expectedChildren = listOf(
            "README.md",
            "gradle",
            "gradlew.bat",
            "gradle.properties",
            "gradlew",
            "build.gradle",
            "settings.gradle",
            "conclave-repo",
            "enclave",
            "host",
            "client",
        ).map(::Path).toSet()

        val actualChildren = outputDir.listDirectoryEntries().map { it.relativeTo(outputDir) }.toSet()
        assertEquals(expectedChildren, actualChildren)

        val enclaveTestFile = outputDir.resolve(enclaveTestFilePath)
        assertTrue(enclaveTestFile.exists())

        val enclaveTestContents = enclaveTestFile.readText()
        testFileStrings.forEach { assertTrue(enclaveTestContents.contains(it)) }

        val clientBuildGradle = outputDir.resolve("client/build.gradle")
        val clientMainClass = "com.megacorp.client.MegaEnclaveClient"
        assertTrue(clientBuildGradle.readText().contains("mainClassName = \"$clientMainClass\""))

        val hostBuildGradle = outputDir.resolve("host/build.gradle")
        val hostMainClass = "com.r3.conclave.host.web.EnclaveWebHost"
        assertTrue(hostBuildGradle.readText().contains("mainClassName = \"$hostMainClass\""))

        val sourceFiles = outputDir
            .walkTopDown()
            .filter { it.extension == expectedExtension }
            .map { it.nameWithoutExtension }
            .toSet()
        assertEquals(setOf("MegaEnclave", "MegaEnclaveTest", "MegaEnclaveClient"), sourceFiles)

        assertTrue(outputDir.walkTopDown().filter { it.extension == invalidExtension }.toList().isEmpty())
    }
}
