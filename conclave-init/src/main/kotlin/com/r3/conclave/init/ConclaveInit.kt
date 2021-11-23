package com.r3.conclave.init

import com.r3.conclave.init.common.deleteRecursively
import com.r3.conclave.init.common.walkTopDown
import com.r3.conclave.init.template.*
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*


object ConclaveInit {
    fun createProject(
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

        copyGradleWrapper(outputRoot)

        templateRoot.deleteRecursively()
    }

    private fun copyGradleWrapper(outputRoot: Path) {
        ZipResource(name = "/gradle-wrapper.zip", outputDir = outputRoot).extractFiles()
        outputRoot.resolve("gradlew").makeExecutable()
    }

    private fun Path.makeExecutable(): Path = setPosixFilePermissions(
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
}

