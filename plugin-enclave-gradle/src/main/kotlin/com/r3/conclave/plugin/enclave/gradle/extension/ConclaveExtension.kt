package com.r3.conclave.plugin.enclave.gradle.extension

import com.r3.conclave.plugin.enclave.gradle.*
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class ConclaveExtension @Inject constructor(objects: ObjectFactory) {
    val productID: Property<Int> = objects.intProperty()
    val revocationLevel: Property<Int> = objects.intProperty()
    val maxHeapSize: Property<String> = objects.stringProperty().convention("256m")
    val maxStackSize: Property<String> = objects.stringProperty().convention("2m")
    val enablePersistentMap: Property<Boolean> = objects.booleanProperty().convention(false)
    val maxPersistentMapSize: Property<String> = objects.stringProperty().convention("16m")
    val inMemoryFileSystemSize: Property<String> = objects.stringProperty().convention("64m")
    val persistentFileSystemSize: Property<String> = objects.stringProperty().convention("0")
    val maxThreads: Property<Int> = objects.intProperty().convention(10)
    val deadlockTimeout: Property<Int> = objects.intProperty().convention(10)
    val release: EnclaveModeExtension = objects.newInstance(BuildType.Release)
    val debug: EnclaveModeExtension = objects.newInstance(BuildType.Debug)
    val simulation: EnclaveModeExtension = objects.newInstance(BuildType.Simulation)

    val supportLanguages: Property<String> = objects.stringProperty().convention("")
    val reflectionConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()
    val serializationConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()

    // We're using a string here so that we can do our own error checking in the plugin code
    val runtime: Property<String> = objects.stringProperty()
    // Constants for the two types we support. Allows the user to not have to use string quotes if they don't want to.
    @Suppress("unused")
    val graalvm = "graalvm"
    @Suppress("unused")
    val gramine = "gramine"

    val kds: KDSExtension = objects.newInstance()

    @Suppress("unused")
    fun release(action: Action<EnclaveModeExtension>) {
        action.execute(release)
    }

    @Suppress("unused")
    fun debug(action: Action<EnclaveModeExtension>) {
        action.execute(debug)
    }
    
    @Suppress("unused")
    fun simulation(action: Action<EnclaveModeExtension>) {
        action.execute(simulation)
    }

    @Suppress("unused")
    fun kds(action: Action<KDSExtension>) {
        action.execute(kds)
    }
}
