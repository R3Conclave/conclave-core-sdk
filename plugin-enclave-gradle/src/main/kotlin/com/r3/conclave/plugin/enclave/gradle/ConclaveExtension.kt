package com.r3.conclave.plugin.enclave.gradle

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
    val release: EnclaveExtension = objects.newInstance(EnclaveExtension::class.java)
    val debug: EnclaveExtension = objects.newInstance(EnclaveExtension::class.java)
    val simulation: EnclaveExtension = objects.newInstance(EnclaveExtension::class.java)

    val runtime: Property<RuntimeType> = objects.property(RuntimeType::class.java)
    val graalvm_native_image = RuntimeType.GraalVMNativeImage
    val avian = RuntimeType.Avian

    val supportLanguages: Property<String> = objects.property(String::class.java).convention("")
    val reflectionConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()
    val serializationConfigurationFiles: ConfigurableFileCollection = objects.fileCollection()

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

open class KDSExtension @Inject constructor(objects: ObjectFactory) {
    val kdsEnclaveConstraint: Property<String> = objects.property(String::class.java)

    val keySpec: KeySpecExtension = objects.newInstance(KeySpecExtension::class.java)

    fun keySpec(action: Action<KeySpecExtension>) {
        action.execute(keySpec)
    }

    val isPresent: Boolean
        get() = kdsEnclaveConstraint.isPresent or keySpec.isPresent
}

open class KeySpecExtension @Inject constructor(objects: ObjectFactory) {
    val masterKeyType: Property<String> = objects.property(String::class.java)

    val policyConstraint: PolicyConstraintExtension = objects.newInstance(PolicyConstraintExtension::class.java)

    fun policyConstraint(action: Action<PolicyConstraintExtension>) {
        action.execute(policyConstraint)
    }

    val isPresent: Boolean
        get() = masterKeyType.isPresent or policyConstraint.isPresent
}

open class PolicyConstraintExtension @Inject constructor(objects: ObjectFactory) {
    val useOwnCodeHash: Property<Boolean> = objects.property(Boolean::class.java)
    val useOwnCodeSignerAndProductID: Property<Boolean> = objects.property(Boolean::class.java)
    val constraint: Property<String> = objects.property(String::class.java)

    val isPresent: Boolean
        get() = useOwnCodeHash.isPresent or useOwnCodeSignerAndProductID.isPresent or constraint.isPresent
}
