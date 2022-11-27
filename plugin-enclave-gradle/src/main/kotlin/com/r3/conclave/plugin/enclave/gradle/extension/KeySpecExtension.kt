package com.r3.conclave.plugin.enclave.gradle.extension

import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

abstract class KeySpecExtension {
    @get:Input
    @get:Optional
    abstract val masterKeyType: Property<String>
    @get:Nested
    abstract val policyConstraint: PolicyConstraintExtension
    
    @Suppress("unused")
    fun policyConstraint(action: Action<PolicyConstraintExtension>) {
        action.execute(policyConstraint)
    }

    val isPresent: Boolean
        @Internal
        get() = masterKeyType.isPresent or policyConstraint.isPresent
}
