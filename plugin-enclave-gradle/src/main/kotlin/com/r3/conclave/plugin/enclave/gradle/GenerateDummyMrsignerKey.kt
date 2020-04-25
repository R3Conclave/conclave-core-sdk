package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class GenerateDummyMrsignerKey @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val opensslTool: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputKey: RegularFileProperty = objects.fileProperty()

    override fun sgxAction() {
        project.exec { spec ->
            spec.commandLine(opensslTool.get(), "genrsa",
                    "-out", outputKey.asFile.get(),
                    "-3",
                    "3072"
            )
        }
    }
}
