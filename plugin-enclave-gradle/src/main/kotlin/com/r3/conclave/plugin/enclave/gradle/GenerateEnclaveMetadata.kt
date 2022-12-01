package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.SHA256Hash
import com.r3.conclave.common.internal.Cursor
import com.r3.conclave.common.internal.SgxCssBody.enclaveHash
import com.r3.conclave.common.internal.SgxEnclaveCss
import com.r3.conclave.common.internal.SgxEnclaveCss.body
import com.r3.conclave.common.internal.SgxEnclaveCss.key
import com.r3.conclave.common.internal.mrsigner
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.readBytes

open class GenerateEnclaveMetadata @Inject constructor(
    objects: ObjectFactory,
    private val plugin: GradleEnclavePlugin,
    private val buildType: BuildType,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    @get:InputFile
    val inputSignedEnclave: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val metadataFile = temporaryDir.toPath().resolve("enclave_css.bin")

        try {
            linuxExec.exec(
                listOf<String>(
                    plugin.signToolPath().absolutePathString(), "dump",
                    "-enclave", inputSignedEnclave.asFile.get().absolutePath,
                    // We don't need this but sgx_sign still requires it be specified.
                    "-dumpfile", "/dev/null",
                    "-cssfile", metadataFile.absolutePathString()
                )
            )
        } finally {
            linuxExec.cleanPreparedFiles()
        }

        val enclaveMetadata = Cursor.wrap(SgxEnclaveCss, metadataFile.readBytes())
        logger.lifecycle("Enclave code hash:   ${SHA256Hash.get(enclaveMetadata[body][enclaveHash].read())}")
        logger.lifecycle("Enclave code signer: ${enclaveMetadata[key].mrsigner}")

        val buildTypeString = buildType.toString().uppercase()
        val buildSecurityString = when (buildType) {
            BuildType.Release -> "SECURE"
            else -> "INSECURE"
        }

        logger.lifecycle("Enclave mode:        $buildTypeString ($buildSecurityString)")
    }
}
