package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.plugin.enclave.gradle.extension.EnclaveExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class SignEnclaveWithKey @Inject constructor(
    objects: ObjectFactory,
    private val plugin: GradleEnclavePlugin,
    private val enclaveExtension: EnclaveExtension,
    private val buildType: BuildType,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputKey: RegularFileProperty = objects.fileProperty()

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
            project.logger.quiet("A signingType of dummyKey has been specified for a release enclave. " +
                    "The resulting enclave will not be loadable on any SGX platform. See Conclave documentation for details")
        }

        if (!OperatingSystem.current().isLinux) {
            try {
                // The input key file may not live in a directory accessible by docker on non-linux
                // systems. Prepare the file so docker can access it if necessary.
                val keyFile = linuxExec.prepareFile(inputKey.asFile.get())

                linuxExec.exec(
                    listOf<String>(
                        plugin.signToolPath().absolutePathString(), "sign",
                        "-key", keyFile.absolutePath,
                        "-enclave", inputEnclave.asFile.get().absolutePath,
                        "-out", outputSignedEnclave.asFile.get().absolutePath,
                        "-config", inputEnclaveConfig.asFile.get().absolutePath
                    )
                )
            } finally {
                linuxExec.cleanPreparedFiles()
            }
        } else {
            commandLine(
                plugin.signToolPath().absolutePathString(), "sign",
                "-key", inputKey.asFile.get(),
                "-enclave", inputEnclave.asFile.get(),
                "-out", outputSignedEnclave.asFile.get(),
                "-config", inputEnclaveConfig.asFile.get()
            )
        }

        project.logger.lifecycle("Signed enclave binary: $signedEnclavePath")
    }
}
