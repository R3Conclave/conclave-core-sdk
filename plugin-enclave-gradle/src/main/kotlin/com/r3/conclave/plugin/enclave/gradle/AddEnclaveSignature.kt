package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class AddEnclaveSignature @Inject constructor(
    objects: ObjectFactory,
    private val plugin: GradleEnclavePlugin,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputSigningMaterial: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveConfig: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputMrsignerPublicKey: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputMrsignerSignature: RegularFileProperty = objects.fileProperty()

    @get:Input
    val buildInDocker: Property<Boolean> = objects.property(Boolean::class.java)

    @get:OutputFile
    val outputSignedEnclave: RegularFileProperty = objects.fileProperty()

    override fun action() {
        if (linuxExec.buildInDocker(buildInDocker)) {
            try {
                // The input key files may not live in a directory accessible by docker.
                // Prepare the files so docker can access them if necessary.
                val mrSignerPublicKey = linuxExec.prepareFile(inputMrsignerPublicKey.asFile.get().toPath())
                val mrSignerSignature = linuxExec.prepareFile(inputMrsignerSignature.asFile.get().toPath())

                linuxExec.exec(
                    listOf<String>(
                        plugin.signToolPath().absolutePathString(), "catsig",
                        "-key", mrSignerPublicKey.absolutePathString(),
                        "-enclave", inputEnclave.asFile.get().absolutePath,
                        "-out", outputSignedEnclave.asFile.get().absolutePath,
                        "-config", inputEnclaveConfig.asFile.get().absolutePath,
                        "-sig", mrSignerSignature.absolutePathString(),
                        "-unsigned", inputSigningMaterial.asFile.get().absolutePath
                    )
                )
            } finally {
                linuxExec.cleanPreparedFiles()
            }
        } else {
            commandLine(
                plugin.signToolPath().absolutePathString(), "catsig",
                "-key", inputMrsignerPublicKey.asFile.get(),
                "-enclave", inputEnclave.asFile.get(),
                "-out", outputSignedEnclave.asFile.get(),
                "-config", inputEnclaveConfig.asFile.get(),
                "-sig", inputMrsignerSignature.asFile.get(),
                "-unsigned", inputSigningMaterial.asFile.get()
            )
        }
    }
}
