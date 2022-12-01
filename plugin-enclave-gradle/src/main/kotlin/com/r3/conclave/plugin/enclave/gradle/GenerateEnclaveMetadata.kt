package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.utilities.internal.toHexString
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class GenerateEnclaveMetadata @Inject constructor(
    objects: ObjectFactory,
    private val plugin: GradleEnclavePlugin,
    private val buildType: BuildType,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    companion object {
        const val ENCLAVE_MRSIGNER_FILE = "mrsigner.txt"
        const val ENCLAVE_MRENCLAVE_FILE = "mrenclave.txt"
    }

    @get:InputFile
    val inputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val mrsignerOutputFile: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val mrenclaveOutputFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val metadataFile = temporaryDir.toPath().resolve("enclave_metadata.txt")

        if (!OperatingSystem.current().isLinux) {
            try {
                linuxExec.exec(
                    listOf<String>(
                        plugin.signToolPath().absolutePathString(), "dump",
                        "-enclave", inputSignedEnclave.asFile.get().absolutePath,
                        "-dumpfile", metadataFile.toAbsolutePath().toString()
                    )
                )
            } finally {
                linuxExec.cleanPreparedFiles()
            }
        } else {
            commandLine(
                plugin.signToolPath().absolutePathString(), "dump",
                "-enclave", inputSignedEnclave.asFile.get(),
                "-dumpfile", metadataFile
            )
        }

        val enclaveMetadata = EnclaveMetadata.parseMetadataFile(metadataFile)

        mrsignerOutputFile.asFile.get().writeText(enclaveMetadata.mrsigner.bytes.toHexString().uppercase())
        mrenclaveOutputFile.asFile.get().writeText(enclaveMetadata.mrenclave.bytes.toHexString().uppercase())

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
