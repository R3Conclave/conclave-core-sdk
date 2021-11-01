package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject

open class GenerateEnclaveProperties @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @Input
    val conclaveExtension: Property<ConclaveExtension> = objects.property(ConclaveExtension::class.java)
    @Input
    val resourceDirectory: Property<String> = objects.property(String::class.java)
    @Input
    val mainClassName: Property<String> = objects.property(String::class.java)

    @get:Internal
    val enclavePropertiesFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val propertiesResourceDir = Paths.get(mainClassName.get().replace('.', '/')).parent
        val propertiesFile = Paths.get(resourceDirectory.get(), propertiesResourceDir.toString(), "enclave.properties")
        val ext = conclaveExtension.get()
        val properties = TreeMap<String, String>()
        properties["productID"] = ext.productID.get().toString()
        properties["revocationLevel"] = ext.revocationLevel.get().toString()
        properties["enablePersistentMap"] = ext.enablePersistentMap.get().toString()
        properties["maxPersistentMapSize"] = GenerateEnclaveConfig.getSizeBytes(ext.maxPersistentMapSize.get()).toString()
        properties["inMemoryFileSystemSize"] = GenerateEnclaveConfig.getSizeBytes(ext.inMemoryFileSystemSize.get()).toString()
        properties["persistentFileSystemSize"] = GenerateEnclaveConfig.getSizeBytes(ext.persistentFileSystemSize.get()).toString()
        Files.createDirectories(propertiesFile.parent)
        FileOutputStream(propertiesFile.toFile()).use {
            it.write("# Build time enclave properties\n".toByteArray())
            for ((key, value) in properties) {
                it.write("$key=$value\n".toByteArray())
            }
        }
        enclavePropertiesFile.set(propertiesFile.toFile())
    }
}
