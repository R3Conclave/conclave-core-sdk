package com.r3.conclave.init

import com.r3.conclave.init.common.deleteRecursively
import com.r3.conclave.init.common.walkTopDown
import com.r3.conclave.init.template.*
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*

object ConclaveInit {
    fun createProject(
        conclaveVersion: String,
        language: Language,
        basePackage: JavaPackage,
        enclaveClass: JavaClass,
        outputRoot: Path,
    ) {
        val templateRoot = ZipResource(name = "/template.zip").extractFiles()
        val templateFiles = templateRoot.walkTopDown()
            .filter { it.isRegularFile() }
            .filter(language::matches)

        val outputFiles =
            TemplatePathTransformer(basePackage, templateRoot, outputRoot, enclaveClass)
                .transform(templateFiles)

        val textTransformer = TemplateTextTransformer(basePackage, enclaveClass)
        (templateFiles zip outputFiles).forEach { (source, destination) ->
            with(destination.parent) { if (!exists()) createDirectories() }

            // If we try to copy a binary file it will be corrupted and this will break
            val contents = source.readText().let(textTransformer::transform)
            destination.writeText(contents)
        }

        generateGradleProperties(outputRoot, conclaveVersion)
        copyGradleWrapper(outputRoot)

        templateRoot.deleteRecursively()
    }

    private fun generateGradleProperties(outputRoot: Path, conclaveVersion: String) {
        val gradlePropertiesFile = outputRoot / "gradle.properties"
        gradlePropertiesFile.writeLines(listOf(
            "# Dependency versions",
            "conclaveVersion=$conclaveVersion",
            "jupiterVersion=5.9.1",
            "slf4jVersion=2.0.3",
        ))
    }

    private fun copyGradleWrapper(outputRoot: Path) {
        ZipResource(name = "/gradle-wrapper.zip", outputDir = outputRoot).extractFiles()
        makeGradlewExecutable(outputRoot)
    }

    private fun makeGradlewExecutable(projectRoot: Path) {
        // `setPosixFilePermissions` will throw UnsupportedOperationException if called on Windows.
        // Windows uses `gradlew.bat` which doesn't need to be made executable, so we just return early.
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            return
        }

        val gradlew = projectRoot.resolve("gradlew")

        try {
            gradlew.setPosixFilePermissions(
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
                )
            )
        } catch (e: Exception) {
            // This warning should be printed at the CLI level, but that would require adding an exception handler
            // which propagates the error without causing the program to terminate.
            println("WARNING: Could not make ./gradlew script executable. Try changing permissions manually with chmod.")
        }
    }
}
