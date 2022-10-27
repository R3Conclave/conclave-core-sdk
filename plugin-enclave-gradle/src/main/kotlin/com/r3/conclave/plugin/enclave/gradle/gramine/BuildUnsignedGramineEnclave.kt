package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.plugin.enclave.gradle.div
import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class BuildUnsignedGramineEnclave @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "java.manifest.template"
        const val MANIFEST_DIRECT = "java.manifest"
        const val METADATA_DIRECT = "enclave-metadata.properties"
        const val GRAMINE_MANIFEST_EXECUTABLE = "gramine-manifest"
    }

    @Input
    val entryPoint: Property<String> = objects.property(String::class.java)

    @Input
    val archLibDirectory: Property<String> = objects.property(String::class.java)

    @Input
    val buildDirectory: Property<String> = objects.property(String::class.java)

    @Input
    val maxThreads: Property<Int> = objects.property(Int::class.java)

    @get:OutputFile
    val outputManifest: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputGramineEnclaveMetadata: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val templateManifest = copyTemplateManifestToBuildDirectory()
        generateGramineDirectManifest(templateManifest.absolutePathString())
        generateGramineEnclaveMetadata()
    }

    private fun copyTemplateManifestToBuildDirectory(): Path {
        val outputManifestTemplateFile = Paths.get(buildDirectory.get()) / MANIFEST_TEMPLATE
        BuildUnsignedGramineEnclave::class.java.copyResource(MANIFEST_TEMPLATE, outputManifestTemplateFile)
        return outputManifestTemplateFile
    }

    private fun generateGramineDirectManifest(templateManifest: String) {
        /**
         * It's possible for Gramine to launch threads internally that conclave won't know about!
         * Because of this, we need to add some safety margin.
         */
        val gramineThreadCount = maxThreads.get() * 2
        val command = listOf(
            GRAMINE_MANIFEST_EXECUTABLE,
            "-Darch_libdir=${archLibDirectory.get()}",
            "-Dentrypoint=${entryPoint.get()}",
            "-DmaxThreads=$gramineThreadCount",
            templateManifest,
            outputManifest.asFile.get().absolutePath
        )
        commandLine(command)
    }

    /**
     * Generate metadata for use in simulation mode.
     * The metadata file is a properties file which is packaged along with the manifest.
     */
    private fun generateGramineEnclaveMetadata() {
        /**
         * We don't use the java Properties class here for two reasons:
         * - It doesn't guarantee consistent order.
         * - It prints the date and time at the top of the file.
         */
        val properties = TreeMap<String, String>().apply {
            put("maxThreads", "${maxThreads.get()}")
            // TODO: Signing key measurement
            put("signingKeyMeasurement", "TODO")
        }

        outputGramineEnclaveMetadata.asFile.get().writer().use { outputStream ->
            for (entry in properties.entries) {
                outputStream.write("${entry.key}=${entry.value}\n")
            }
        }
    }
}
