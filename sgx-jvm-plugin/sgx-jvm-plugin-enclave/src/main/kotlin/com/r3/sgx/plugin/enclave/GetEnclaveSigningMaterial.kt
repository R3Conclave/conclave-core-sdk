package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

open class GetEnclaveSigningMaterial @Inject constructor(objects: ObjectFactory) : SgxTask() {

    private companion object {
       const val SIGNATURE_DATE_OFFSET = 20 //< Offset of date field in signing input structure
    }

    @get:InputFile
    val inputEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val signTool: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveConfig: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputSigningMaterial: RegularFileProperty = objects.fileProperty()

    @get:Input
    val signatureDate: Property<Date> = objects.property(Date::class.java)

    override fun sgxAction() {
        project.exec { spec ->
            spec.commandLine(signTool.asFile.get(), "gendata",
                    "-enclave", inputEnclave.asFile.get(),
                    "-out", outputSigningMaterial.asFile.get(),
                    "-config", inputEnclaveConfig.asFile.get()
            )
        }
       postProcess()
    }

    private fun postProcess() {
        val signingMaterialFile = outputSigningMaterial.asFile.get()
        if (!signingMaterialFile.exists()) {
            throw GradleException("sign_tool output is missing")
        }
        val signatureDateStr = SimpleDateFormat("yyyymmdd").format(signatureDate.get())
        logger.info("Enclave signature date: $signatureDateStr")
        val data = signingMaterialFile.readBytes()
        with(ByteBuffer.wrap(data)) {
            position(SIGNATURE_DATE_OFFSET)
            order(ByteOrder.LITTLE_ENDIAN)
            val encodedSigDate = Integer.parseInt(signatureDateStr, 16)
            putInt(encodedSigDate)
        }
        signingMaterialFile.writeBytes(data)
    }
}
