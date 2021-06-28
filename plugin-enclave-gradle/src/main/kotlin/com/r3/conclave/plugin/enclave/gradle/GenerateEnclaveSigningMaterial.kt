package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.os.OperatingSystem
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

open class GenerateEnclaveSigningMaterial @Inject constructor(
        objects: ObjectFactory,
        private val linuxExec: LinuxExec
) : ConclaveTask() {
    private companion object {
       const val SIGNATURE_DATE_OFFSET = 20 //< Offset of date field in signing input structure
    }

    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val signTool: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveConfig: RegularFileProperty = objects.fileProperty()

    @get:Input
    val signatureDate: Property<Date> = objects.property(Date::class.java)

    @get:OutputFile
    val outputSigningMaterial: RegularFileProperty = objects.fileProperty()

    @get:LocalState
    var outputSigningMaterialTmp: File? = null

    override fun action() {

        if (OperatingSystem.current().isWindows) {
            // The signing material file may not live in a directory accessible by docker on non-linux
            // systems. Prepare the file so docker can access it if necessary.
            outputSigningMaterialTmp = linuxExec.prepareFile(outputSigningMaterial.asFile.get())
            if (outputSigningMaterialTmp == null) {
                throw GradleException("Could not create temporary file for output signing material.")
            }

            linuxExec.exec(
                    listOf<String> (
                        signTool.asFile.get().absolutePath, "gendata",
                        "-enclave", inputEnclave.asFile.get().absolutePath,
                        "-out", outputSigningMaterialTmp!!.absolutePath,
                        "-config", inputEnclaveConfig.asFile.get().absolutePath
                    )
                )
        } else {
            commandLine(
                    signTool.asFile.get(), "gendata",
                    "-enclave", inputEnclave.asFile.get(),
                    "-out", outputSigningMaterial.asFile.get(),
                    "-config", inputEnclaveConfig.asFile.get()
                )
        }
       postProcess()
    }

    private fun postProcess() {
        try {
            val signingMaterialFile = outputSigningMaterial.asFile.get()
            val data = if (OperatingSystem.current().isWindows) {
                if ((outputSigningMaterialTmp == null) || !outputSigningMaterialTmp!!.exists()) {
                    throw GradleException("sign_tool output is missing")
                }
                outputSigningMaterialTmp!!.readBytes()
            } else {
                if (!signingMaterialFile.exists()) {
                    throw GradleException("sign_tool output is missing")
                }
                signingMaterialFile.readBytes()
            }
            val signatureDateStr = SimpleDateFormat("yyyymmdd").format(signatureDate.get())
            logger.info("Enclave signature date: $signatureDateStr")
            with(ByteBuffer.wrap(data)) {
                position(SIGNATURE_DATE_OFFSET)
                order(ByteOrder.LITTLE_ENDIAN)
                val encodedSigDate = Integer.parseInt(signatureDateStr, 16)
                putInt(encodedSigDate)
            }
            signingMaterialFile.writeBytes(data)
            project.logger.lifecycle("Enclave signing materials: ${signingMaterialFile.absolutePath}")
        } finally {
            if (OperatingSystem.current().isWindows) {
                linuxExec.cleanPreparedFiles()
            }
        }

    }
}
