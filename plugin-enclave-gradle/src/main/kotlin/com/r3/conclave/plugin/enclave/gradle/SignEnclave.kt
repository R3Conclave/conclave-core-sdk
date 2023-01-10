package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class SignEnclave @Inject constructor(
    objects: ObjectFactory,
    private val plugin: GradleEnclavePlugin,
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

    @get:Input
    val buildInDocker: Property<Boolean> = objects.property(Boolean::class.java)

    override fun action() {
        if (linuxExec.buildInDocker(buildInDocker)) {
            try {
                // The input key file may not live in a directory accessible by docker.
                // Prepare the file so docker can access it if necessary.
                val keyFile = linuxExec.prepareFile(inputKey.asFile.get().toPath())

                linuxExec.exec(
                    listOf<String>(
                        plugin.signToolPath().absolutePathString(), "sign",
                        "-key", keyFile.absolutePathString(),
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
