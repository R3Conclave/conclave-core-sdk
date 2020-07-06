package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class BuildUnsignedEnclave @Inject constructor(
        objects: ObjectFactory,
        private val buildUnsignedAvianEnclave: BuildUnsignedAvianEnclave,
        private val buildUnsignedGraalEnclave: NativeImage) : ConclaveTask() {

    @get:Input
    val runtime: Property<RuntimeType> = objects.property(RuntimeType::class.java)

    @get:OutputFile
    val outputEnclave: RegularFileProperty = objects.fileProperty()

    override fun action() {
        when (runtime.get()) {
            RuntimeType.GraalVMNativeImage -> {
                buildUnsignedGraalEnclave.action()
            }
            else -> {
                buildUnsignedAvianEnclave.action()
            }
        }
    }

}