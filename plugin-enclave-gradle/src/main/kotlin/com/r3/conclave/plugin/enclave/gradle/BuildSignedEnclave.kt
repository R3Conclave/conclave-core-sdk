package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

/**
 * This is a noop task that depends on either SignEnclave or AddEnclaveSignature depending on settings
 */
open class BuildSignedEnclave @Inject constructor(objects: ObjectFactory) : ConclaveTask() {

    @get:OutputFile
    val outputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:Internal
    val signedEnclavePath: String get() = outputSignedEnclave.asFile.get().absolutePath

    override fun action() {
    }
}
