package com.r3.conclave.plugin.enclave.gradle.extension

import com.r3.conclave.plugin.enclave.gradle.*
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import javax.inject.Inject

open class ConclaveExtension @Inject constructor(objects: ObjectFactory) {
    @get:Input
    val productID: Property<Int> = objects.property(Int::class.java)
    @get:Input
    val revocationLevel: Property<Int> = objects.property(Int::class.java)
    @get:Input
    val maxHeapSize: Property<String> = objects.property(String::class.java).convention("256m")
    @get:Input
    val maxStackSize: Property<String> = objects.property(String::class.java).convention("2m")
    @get:Input
    val enablePersistentMap: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    @get:Input
    val maxPersistentMapSize: Property<String> = objects.property(String::class.java).convention("16m")
    @get:Input
    val inMemoryFileSystemSize: Property<String> = objects.property(String::class.java).convention("64m")
    @get:Input
    val persistentFileSystemSize: Property<String> = objects.property(String::class.java).convention("0")
    @get:Input
    val maxThreads: Property<Int> = objects.property(Int::class.java).convention(10)
    @get:Input
    val deadlockTimeout: Property<Int> = objects.property(Int::class.java).convention(10)
    @get:Input
    val supportLanguages: Property<String> = objects.property(String::class.java).convention("")
    @get:InputFiles
    val reflectionConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()
    @get:InputFiles
    val serializationConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()
    // We're using a string here so that we can do our own error checking in the plugin code
    // TODO We are a few enum properties whch should have all the same parsing logic
    @get:Input
    val runtime: Property<String> = objects.property(String::class.java)
    // Constants for the two types we support. Allows the user to not have to use string quotes if they don't want to.
    @Suppress("unused")
    @get:Internal
    val graalvm = "graalvm"
    @Suppress("unused")
    @get:Internal
    val gramine = "gramine"

    @get:Nested
    val kds: KDSExtension = objects.newInstance(KDSExtension::class.java)

    @get:Nested
    val release: EnclaveExtension = objects.newInstance(BuildType.Release)
    @get:Nested
    val debug: EnclaveExtension = objects.newInstance(BuildType.Debug)
    @get:Nested
    val simulation: EnclaveExtension = objects.newInstance(BuildType.Simulation)

    fun release(action: Action<EnclaveExtension>) {
        action.execute(release)
    }

    fun debug(action: Action<EnclaveExtension>) {
        action.execute(debug)
    }

    fun simulation(action: Action<EnclaveExtension>) {
        action.execute(simulation)
    }

    fun kds(action: Action<KDSExtension>) {
        action.execute(kds)
    }
}
