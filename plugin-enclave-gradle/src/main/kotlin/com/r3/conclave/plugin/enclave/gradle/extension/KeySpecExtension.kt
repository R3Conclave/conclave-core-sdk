package com.r3.conclave.plugin.enclave.gradle.extension

import com.r3.conclave.plugin.enclave.gradle.newInstance
import com.r3.conclave.plugin.enclave.gradle.stringProperty
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class KeySpecExtension @Inject constructor(objects: ObjectFactory) {
    val masterKeyType: Property<String> = objects.stringProperty()
    val policyConstraint: PolicyConstraintExtension = objects.newInstance()
    
    @Suppress("unused")
    fun policyConstraint(action: Action<PolicyConstraintExtension>) {
        action.execute(policyConstraint)
    }

    val isPresent: Boolean
        get() = masterKeyType.isPresent or policyConstraint.isPresent
}
