package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import javax.inject.Inject

open class SignEnclave @Inject constructor(objects: ObjectFactory) : SgxTask() {
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

    override fun sgxAction() {
        project.exec { spec ->
            spec.commandLine(signTool.asFile.get(), "sign",
                "-key", inputKey.asFile.get(),
                "-enclave", inputEnclave.asFile.get(),
                "-out", outputSignedEnclave.asFile.get(),
                "-config", inputEnclaveConfig.asFile.get()
            )
        }
        project.logger.lifecycle("Signed enclave binary: $signedEnclavePath")
    }
}
