package com.r3.conclave.plugin.enclave.gradle.extension

import com.r3.conclave.plugin.enclave.gradle.newInstance
import com.r3.conclave.plugin.enclave.gradle.stringProperty
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class KDSExtension @Inject constructor(objects: ObjectFactory) {
    val kdsEnclaveConstraint: Property<String> = objects.stringProperty()
    val keySpec: KeySpecExtension = objects.newInstance()
    val persistenceKeySpec: KeySpecExtension = objects.newInstance()

    @Suppress("unused")
    fun keySpec(action: Action<KeySpecExtension>) {
        action.execute(keySpec)
    }

    @Suppress("unused")
    fun persistenceKeySpec(action: Action<KeySpecExtension>) {
        action.execute(persistenceKeySpec)
    }

    val isPresent: Boolean
        get() = kdsEnclaveConstraint.isPresent or keySpec.isPresent or persistenceKeySpec.isPresent
}
