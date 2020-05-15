package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class SignEnclave @Inject constructor(
    objects: ObjectFactory,
    private val enclaveExtension: EnclaveExtension,
    private val buildType: BuildType
) : ConclaveTask() {
    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputKey: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val signTool: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveConfig: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:Internal
    val signedEnclavePath: String get() = outputSignedEnclave.asFile.get().absolutePath

    override fun action() {
        if (enclaveExtension.signingType.get() == SigningType.DummyKey && buildType == BuildType.Release) {
            // Using 'quiet' logging type for 'Important information messages'.
            // See https://docs.gradle.org/current/userguide/logging.html.
            project.logger.quiet("A signingType of dummyKey has been specified for a release enclave. The resulting enclave will not be loadable on any SGX platform. See Conclave documentation for details");
        }

        project.exec { spec ->
            spec.commandLine(signTool.get(), "sign",
                "-key", inputKey.asFile.get(),
                "-enclave", inputEnclave.asFile.get(),
                "-out", outputSignedEnclave.asFile.get(),
                "-config", inputEnclaveConfig.asFile.get()
            )
        }
        project.logger.lifecycle("Signed enclave binary: $signedEnclavePath")
    }
}
