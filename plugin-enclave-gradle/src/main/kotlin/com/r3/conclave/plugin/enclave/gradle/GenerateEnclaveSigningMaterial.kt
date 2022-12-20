package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class GenerateEnclaveSigningMaterial @Inject constructor(
    objects: ObjectFactory,
    private val plugin: GradleEnclavePlugin,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    private companion object {
        const val SIGNATURE_DATE_OFFSET = 20 //< Offset of date field in signing input structure
    }

    @get:Input
    val useInternalDockerRepo: Property<Boolean> = objects.property(Boolean::class.java)

    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveConfig: RegularFileProperty = objects.fileProperty()

    @get:Input
    val signatureDate: Property<Date> = objects.property(Date::class.java)

    @get:OutputFile
    val outputSigningMaterial: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val dockerOutputSigningFile: File?
        if (useInternalDockerRepo.get()) {
            // The signing material file may not live in a directory accessible by docker.
            // Prepare the file so docker can access it if necessary.
            dockerOutputSigningFile = linuxExec.prepareFile(outputSigningMaterial.asFile.get())

            linuxExec.exec(
                listOf<String>(
                    plugin.signToolPath().absolutePathString(), "gendata",
                    "-enclave", inputEnclave.asFile.get().absolutePath,
                    "-out", dockerOutputSigningFile.absolutePath,
                    "-config", inputEnclaveConfig.asFile.get().absolutePath
                )
            )
        } else {
            dockerOutputSigningFile = null
            commandLine(
                plugin.signToolPath().absolutePathString(), "gendata",
                "-enclave", inputEnclave.asFile.get(),
                "-out", outputSigningMaterial.asFile.get(),
                "-config", inputEnclaveConfig.asFile.get()
            )
        }
        try {
            postProcess(dockerOutputSigningFile)
        } finally {
            linuxExec.cleanPreparedFiles()
        }
    }

    private fun postProcess(dockerOutputSigningFile: File?) {
        val outputSigningMaterial = outputSigningMaterial.asFile.get()
        val signingFile = dockerOutputSigningFile ?: outputSigningMaterial
        if (!signingFile.exists()) {
            throw GradleException("sign_tool output is missing")
        }
        val data = signingFile.readBytes()
        val signatureDateStr = SimpleDateFormat("yyyymmdd").format(signatureDate.get())
        logger.info("Enclave signature date: $signatureDateStr")
        with(ByteBuffer.wrap(data)) {
            position(SIGNATURE_DATE_OFFSET)
            order(ByteOrder.LITTLE_ENDIAN)
            val encodedSigDate = Integer.parseInt(signatureDateStr, 16)
            putInt(encodedSigDate)
        }
        outputSigningMaterial.writeBytes(data)
        project.logger.lifecycle("Enclave signing materials: ${outputSigningMaterial.absolutePath}")
    }
}
