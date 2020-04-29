package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class ConclaveExtension @Inject constructor(objects: ObjectFactory) {
    val release: EnclaveExtension = objects.newInstance(EnclaveExtension::class.java)
    val debug: EnclaveExtension = objects.newInstance(EnclaveExtension::class.java)
    val simulation: EnclaveExtension = objects.newInstance(EnclaveExtension::class.java)

    fun release(action: Action<EnclaveExtension>) {
        action.execute(release)
    }

    fun debug(action: Action<EnclaveExtension>) {
        action.execute(debug)
    }

    fun simulation(action: Action<EnclaveExtension>) {
        action.execute(simulation)
    }
}
