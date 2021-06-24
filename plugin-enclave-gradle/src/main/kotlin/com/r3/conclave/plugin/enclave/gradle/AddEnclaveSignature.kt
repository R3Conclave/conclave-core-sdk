package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject

open class AddEnclaveSignature @Inject constructor(
        objects: ObjectFactory,
        private val linuxExec: LinuxExec
) : ConclaveTask() {
    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputSigningMaterial: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val signTool: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveConfig: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputMrsignerPublicKey: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputMrsignerSignature: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:Internal
    val signedEnclavePath: String get() = outputSignedEnclave.asFile.get().absolutePath

    override fun action() {
        if (OperatingSystem.current().isWindows) {
            try {
                // The input key files may not live in a directory accessible by docker on non-linux
                // systems. Prepare the files so docker can access them if necessary.
                val mrSignerPublicKey = linuxExec.prepareFile(inputMrsignerPublicKey.asFile.get())
                val mrSignerSignature = linuxExec.prepareFile(inputMrsignerSignature.asFile.get())

                linuxExec.exec(
                        listOf<String>(
                                signTool.asFile.get().absolutePath, "catsig",
                                "-key", mrSignerPublicKey.absolutePath,
                                "-enclave", inputEnclave.asFile.get().absolutePath,
                                "-out", outputSignedEnclave.asFile.get().absolutePath,
                                "-config", inputEnclaveConfig.asFile.get().absolutePath,
                                "-sig", mrSignerSignature.absolutePath,
                                "-unsigned", inputSigningMaterial.asFile.get().absolutePath
                        )
                )
            } finally {
                linuxExec.cleanPreparedFiles()
            }
        } else {
            commandLine(signTool.asFile.get(), "catsig",
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
