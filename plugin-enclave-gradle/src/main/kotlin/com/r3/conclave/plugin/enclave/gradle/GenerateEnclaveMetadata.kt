package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject

open class GenerateEnclaveMetadata @Inject constructor(
        objects: ObjectFactory,
        private val buildType: BuildType,
        private val linuxExec: LinuxExec
) : ConclaveTask() {
    @get:InputFile
    val inputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputSignTool: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val metadataFile = temporaryDir.toPath().resolve("enclave_metadata.txt")

        if (OperatingSystem.current().isWindows) {
            try {
                linuxExec.exec(
                        listOf<String>(
                                inputSignTool.asFile.get().absolutePath, "dump",
                                "-enclave", inputSignedEnclave.asFile.get().absolutePath,
                                "-dumpfile", metadataFile.toAbsolutePath().toString()
                        )
                )
            } finally {
                linuxExec.cleanPreparedFiles()
            }
        } else {
            commandLine(
                    inputSignTool.asFile.get(), "dump",
                    "-enclave", inputSignedEnclave.asFile.get(),
                    "-dumpfile", metadataFile
            )
        }

        val enclaveMetadata = EnclaveMetadata.parseMetadataFile(metadataFile)
        logger.lifecycle("Enclave code hash:   ${enclaveMetadata.mrenclave}")
        logger.lifecycle("Enclave code signer: ${enclaveMetadata.mrsigner}")

        val buildTypeString = buildType.toString().uppercase()
        val buildSecurityString = when(buildType) {
            BuildType.Release -> "SECURE"
            else -> "INSECURE"
        }

        logger.lifecycle("Enclave mode:        $buildTypeString ($buildSecurityString)")
    }
}
