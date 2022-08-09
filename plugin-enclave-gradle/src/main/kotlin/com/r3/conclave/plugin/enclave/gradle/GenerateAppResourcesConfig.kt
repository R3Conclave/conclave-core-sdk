package com.r3.conclave.plugin.enclave.gradle

import io.github.classgraph.ClassGraph
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class GenerateAppResourcesConfig @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val jarFile: RegularFileProperty = objects.fileProperty()

    @OutputFile
    val appResourcesConfigFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        logger.info("Scanning for resource files...")

        val resourcePaths = ClassGraph()
                .overrideClasspath(jarFile.get().asFile)
                .scan().use { scanResult ->
                    scanResult.allResources.paths
                            .filterNot { it.endsWith(".class") }
                            .map { it.replace("\\", "/") }
                }

        logger.info("Discovered ${resourcePaths.size} resources:")
        logger.info(resourcePaths.joinToString(","))

        ResourcesConfig.create(includes = resourcePaths).writeToFile(appResourcesConfigFile.get().asFile)
    }
}
