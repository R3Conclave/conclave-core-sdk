package com.r3.conclave.plugin.enclave.gradle.extension

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

abstract class PolicyConstraintExtension {
    @get:Input
    @get:Optional
    abstract val useOwnCodeHash: Property<Boolean>
    @get:Input
    @get:Optional
    abstract val useOwnCodeSignerAndProductID: Property<Boolean>
    @get:Input
    @get:Optional
    abstract val constraint: Property<String>

    val isPresent: Boolean
        @Internal
        get() = useOwnCodeHash.isPresent or useOwnCodeSignerAndProductID.isPresent or constraint.isPresent
}
