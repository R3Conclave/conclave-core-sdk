package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class AddEnclaveSignature @Inject constructor(objects: ObjectFactory) : SgxTask() {
    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputPublicKeyPem: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputSignature: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputSigningMaterial: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val signTool: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveConfig: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:Internal
    val signedEnclavePath: String get() = outputSignedEnclave.asFile.get().absolutePath

    override fun sgxAction() {
        project.exec { spec ->
            spec.commandLine(signTool.asFile.get(), "catsig",
                    "-key", inputPublicKeyPem.asFile.get(),
                    "-enclave", inputEnclave.asFile.get(),
                    "-out", outputSignedEnclave.asFile.get(),
                    "-config", inputEnclaveConfig.asFile.get(),
                    "-sig", inputSignature.asFile.get(),
                    "-unsigned", inputSigningMaterial.asFile.get()
            )
        }
    }
}
