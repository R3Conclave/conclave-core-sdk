package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class GenerateDummyMrsignerKey @Inject constructor(objects: ObjectFactory) : SgxTask() {

    @get:OutputFile
    val outputKey: RegularFileProperty = objects.fileProperty()

    override fun sgxAction() {
        project.exec { spec ->
            spec.commandLine("/usr/bin/env", "openssl", "genrsa",
                    "-out", outputKey.asFile.get(),
                    "-3",
                    "3072"
            )
        }
    }
}
