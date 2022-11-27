package com.r3.conclave.plugin.enclave.gradle.extension

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

abstract class KDSExtension {
    @get:Input
    @get:Optional
    abstract val kdsEnclaveConstraint: Property<String>
    @get:Nested
    abstract val persistenceKeySpec: KeySpecExtension

    @Suppress("unused")
    fun persistenceKeySpec(action: Action<KeySpecExtension>) {
        action.execute(persistenceKeySpec)
    }

    val isPresent: Boolean
        @Internal
        get() = kdsEnclaveConstraint.isPresent or persistenceKeySpec.isPresent
}
