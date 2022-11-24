package com.r3.conclave.plugin.enclave.gradle.extension

import com.r3.conclave.plugin.enclave.gradle.*
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class ConclaveExtension @Inject constructor(objects: ObjectFactory) {
    val productID: Property<Int> = objects.property(Int::class.java)
    val revocationLevel: Property<Int> = objects.property(Int::class.java)
    val maxHeapSize: Property<String> = objects.property(String::class.java).convention("256m")
    val maxStackSize: Property<String> = objects.property(String::class.java).convention("2m")
    val enablePersistentMap: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val maxPersistentMapSize: Property<String> = objects.property(String::class.java).convention("16m")
    val inMemoryFileSystemSize: Property<String> = objects.property(String::class.java).convention("64m")
    val persistentFileSystemSize: Property<String> = objects.property(String::class.java).convention("0")
    val maxThreads: Property<Int> = objects.property(Int::class.java).convention(10)
    val deadlockTimeout: Property<Int> = objects.property(Int::class.java).convention(10)
    val release: EnclaveExtension = objects.newInstance(BuildType.Release)
    val debug: EnclaveExtension = objects.newInstance(BuildType.Debug)
    val simulation: EnclaveExtension = objects.newInstance(BuildType.Simulation)

    val supportLanguages: Property<String> = objects.property(String::class.java).convention("")
    val reflectionConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()
    val serializationConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()

    // We're using a string here so that we can do our own error checking in the plugin code
    val runtime: Property<String> = objects.property(String::class.java)
    // Constants for the two types we support. Allows the user to not have to use string quotes if they don't want to.
    val graalvm = "graalvm"
    val gramine = "gramine"

    val kds: KDSExtension = objects.newInstance(KDSExtension::class.java)

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
