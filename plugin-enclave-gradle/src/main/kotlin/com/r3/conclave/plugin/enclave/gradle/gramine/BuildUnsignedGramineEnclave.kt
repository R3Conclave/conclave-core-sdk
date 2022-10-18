package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.plugin.enclave.gradle.div
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class BuildUnsignedGramineEnclave @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "bash.manifest.template"
        const val MANIFEST_DIRECT = "bash.manifest"
        const val GRAMINE_MANIFEST_EXECUTABLE = "gramine-manifest"
    }

    @Input
    val entryPoint: Property<String> = objects.property(String::class.java)

    @Input
    val archLibDirectory: Property<String> = objects.property(String::class.java)

    @Input
    val buildDirectory: Property<String> = objects.property(String::class.java)

    @get:OutputFile
    val outputManifest: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val templateManifest = copyTemplateManifestToBuildDirectory()
        generateGramineDirectManifest(templateManifest.absolutePathString())
    }

    private fun copyTemplateManifestToBuildDirectory(): Path {
        val outputManifestTemplateFile = Paths.get(buildDirectory.get()) / MANIFEST_TEMPLATE

        BuildUnsignedGramineEnclave::class.java.getResourceAsStream(MANIFEST_TEMPLATE)!!.use {
            logger.info("Copying $MANIFEST_TEMPLATE to build directory")
            Files.copy(it, outputManifestTemplateFile, StandardCopyOption.REPLACE_EXISTING)
        }
        return outputManifestTemplateFile
    }

    private fun generateGramineDirectManifest(templateManifest: String) {
        val command = listOf(
            GRAMINE_MANIFEST_EXECUTABLE,
            "-Darch_libdir=${archLibDirectory.get()}",
            "-Dentrypoint=${entryPoint.get()}",
            templateManifest,
            outputManifest.asFile.get().absolutePath
        )
	commandLine(command)
    }
}
