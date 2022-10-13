package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class BuildUnsignedEnclave @Inject constructor(
        objects: ObjectFactory
    ) : ConclaveTask() {

    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputEnclave: RegularFileProperty = objects.fileProperty()

    override fun action() {
        // This task exists purely so task dependencies for building the enclave can be set
        // dynamically at runtime by setting inputEnclave to the output of the required task
        // based on the value of conclaveExtension.runtime. see buildUnsignedEnclaveTask
        // in GradleEnclavePlugin.kt.
    }

}
