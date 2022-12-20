package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxCssBody.enclaveHash
import com.r3.conclave.common.internal.SgxEnclaveCss
import com.r3.conclave.common.internal.SgxEnclaveCss.body
import com.r3.conclave.common.internal.SgxEnclaveCss.key
import com.r3.conclave.common.internal.mrsigner
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.readBytes

open class GenerateEnclaveMetadata @Inject constructor(
    objects: ObjectFactory,
    private val plugin: GradleEnclavePlugin,
    private val enclaveMode: EnclaveMode,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    @get:InputFile
    val inputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:Input
    val useInternalDockerRepo: Property<Boolean> = objects.property(Boolean::class.java)

    override fun action() {
        // TODO use -cssfile as it produces the binary SIGSTRUCT which can be read directly using SgxMetadataEnclaveCss.
        //  See TestUtils.getEnclaveSigstruct in the integration tests.
        val metadataFile = temporaryDir.toPath().resolve("enclave_css.bin")

        if (linuxExec.buildInDocker(useInternalDockerRepo)) {
            try {
                linuxExec.exec(
                    listOf<String>(
                        plugin.signToolPath().absolutePathString(), "dump",
                        "-enclave", inputSignedEnclave.asFile.get().absolutePath,
                        // We don't need this but sgx_sign still requires it to be specified.
                        "-dumpfile", "/dev/null",
                        "-cssfile", metadataFile.absolutePathString()
                    )
                )
            } finally {
                linuxExec.cleanPreparedFiles()
            }
        } else {
            commandLine(
                plugin.signToolPath().absolutePathString(), "dump",
                "-enclave", inputSignedEnclave.asFile.get(),
                // We don't need this but sgx_sign still requires it be specified.
                "-dumpfile", "/dev/null",
                "-cssfile", metadataFile.absolutePathString()
            )
        }

        val enclaveMetadata = Cursor.wrap(SgxEnclaveCss, metadataFile.readBytes())
        logger.lifecycle("Enclave code hash:   ${SHA256Hash.get(enclaveMetadata[body][enclaveHash].read())}")
        logger.lifecycle("Enclave code signer: ${enclaveMetadata[key].mrsigner}")

        val buildSecurityString = if (enclaveMode == EnclaveMode.RELEASE) "SECURE" else "INSECURE"
        logger.lifecycle("Enclave mode:        $enclaveMode ($buildSecurityString)")
    }
}
