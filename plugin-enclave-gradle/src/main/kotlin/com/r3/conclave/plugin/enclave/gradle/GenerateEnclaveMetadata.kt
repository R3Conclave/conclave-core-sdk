package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.testing.internal.EnclaveMetadata
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import javax.inject.Inject

open class GenerateEnclaveMetadata @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val inputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputSignTool: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val metadataFile = temporaryDir.toPath().resolve("enclave_metadata.txt")
        project.exec { spec ->
            spec.commandLine(
                    inputSignTool.asFile.get(), "dump",
                    "-enclave", inputSignedEnclave.asFile.get(),
                    "-dumpfile", metadataFile
            )
        }
        val enclaveMetadata = EnclaveMetadata.parseMetadataFile(metadataFile)
        logger.lifecycle("Enclave code hash:   ${enclaveMetadata.mrenclave}")
        logger.lifecycle("Enclave code signer: ${enclaveMetadata.mrsigner}")
    }
}
