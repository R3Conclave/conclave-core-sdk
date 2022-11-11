package com.r3.conclave.plugin.enclave.gradle.extension

import com.r3.conclave.plugin.enclave.gradle.booleanProperty
import com.r3.conclave.plugin.enclave.gradle.stringProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class PolicyConstraintExtension @Inject constructor(objects: ObjectFactory) {
    val useOwnCodeHash: Property<Boolean> = objects.booleanProperty()
    val useOwnCodeSignerAndProductID: Property<Boolean> = objects.booleanProperty()
    val constraint: Property<String> = objects.stringProperty()

    val isPresent: Boolean
        get() = useOwnCodeHash.isPresent or useOwnCodeSignerAndProductID.isPresent or constraint.isPresent
}
