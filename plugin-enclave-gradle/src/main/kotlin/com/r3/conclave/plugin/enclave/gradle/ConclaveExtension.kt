package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.EnclaveMode
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
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
    val maxThreads: Property<Int> = objects.property(Int::class.java).convention(100)
    @get:Input
    val deadlockTimeout: Property<Int> = objects.property(Int::class.java).convention(10)
    @get:Input
    val buildInDocker: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    @get:Input
    val supportLanguages: Property<String> = objects.property(String::class.java).convention("")
    @get:Input
    val extraJavaModules: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    @get:InputFiles
    val reflectionConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()
    @get:InputFiles
    val serializationConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()
    // We're using a string here so that we can do our own error checking in the plugin code
    // TODO There are a few enum properties which should have all the same parsing logic
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

    @get:Internal
    val release: EnclaveExtension = objects.newInstance(EnclaveMode.RELEASE)
    @get:Internal
    val debug: EnclaveExtension = objects.newInstance(EnclaveMode.DEBUG)
    @get:Internal
    val simulation: EnclaveExtension = objects.newInstance(EnclaveMode.SIMULATION)

    @Suppress("unused")
    fun release(action: Action<EnclaveExtension>) {
        action.execute(release)
    }

    @Suppress("unused")
    fun debug(action: Action<EnclaveExtension>) {
        action.execute(debug)
    }

    @Suppress("unused")
    fun simulation(action: Action<EnclaveExtension>) {
        action.execute(simulation)
    }

    @Suppress("unused")
    fun kds(action: Action<KDSExtension>) {
        action.execute(kds)
    }
}

open class KDSExtension @Inject constructor(objects: ObjectFactory) {
    @get:Input
    @get:Optional
    val kdsEnclaveConstraint: Property<String> = objects.property(String::class.java)
    @get:Nested
    val keySpec: KeySpecExtension = objects.newInstance(KeySpecExtension::class.java)
    @get:Nested
    val persistenceKeySpec: KeySpecExtension = objects.newInstance(KeySpecExtension::class.java)

    @Suppress("unused")
    fun keySpec(action: Action<KeySpecExtension>) {
        action.execute(keySpec)
    }

    @Suppress("unused")
    fun persistenceKeySpec(action: Action<KeySpecExtension>) {
        action.execute(persistenceKeySpec)
    }

    val isPresent: Boolean
        @Internal
        get() = kdsEnclaveConstraint.isPresent or persistenceKeySpec.isPresent
}

open class KeySpecExtension @Inject constructor(objects: ObjectFactory) {
    @get:Input
    @get:Optional
    val masterKeyType: Property<String> = objects.property(String::class.java)
    @get:Nested
    val policyConstraint: PolicyConstraintExtension = objects.newInstance(PolicyConstraintExtension::class.java)

    @Suppress("unused")
    fun policyConstraint(action: Action<PolicyConstraintExtension>) {
        action.execute(policyConstraint)
    }

    val isPresent: Boolean
        @Internal
        get() = masterKeyType.isPresent or policyConstraint.isPresent
}

open class PolicyConstraintExtension @Inject constructor(objects: ObjectFactory) {
    @get:Input
    @get:Optional
    val useOwnCodeHash: Property<Boolean> = objects.property(Boolean::class.java)
    @get:Input
    @get:Optional
    val useOwnCodeSignerAndProductID: Property<Boolean> = objects.property(Boolean::class.java)
    @get:Input
    @get:Optional
    val constraint: Property<String> = objects.property(String::class.java)

    val isPresent: Boolean
        @Internal
        get() = useOwnCodeHash.isPresent or useOwnCodeSignerAndProductID.isPresent or constraint.isPresent
}
