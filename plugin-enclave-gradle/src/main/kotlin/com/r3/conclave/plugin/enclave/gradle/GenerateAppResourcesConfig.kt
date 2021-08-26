package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import java.io.File
import javax.inject.Inject

open class GenerateAppResourcesConfig @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @Input
    val resourcesDirectory: Property<File> = objects.property(File::class.java)

    @OutputFile
    val appResourcesConfigFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val resourcesDirectory = resourcesDirectory.get()
        val resourceFiles = resourcesDirectory.walkTopDown().filter { it.isFile }
        val resourceFilesRelative = resourceFiles.map { it.relativeTo(resourcesDirectory).toString() }.toList()

        ResourcesConfig.create(includes = resourceFilesRelative).writeToFile(appResourcesConfigFile.get().asFile)
    }
}