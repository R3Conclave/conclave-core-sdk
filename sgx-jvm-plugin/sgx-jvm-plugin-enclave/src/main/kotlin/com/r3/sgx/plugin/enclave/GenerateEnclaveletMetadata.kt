package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import java.io.EOFException
import java.io.File
import javax.inject.Inject

/**
 * This task generates a yaml metadata file containing the enclavelet class name and measurement.
 */
open class GenerateEnclaveletMetadata @Inject constructor(objects: ObjectFactory) : SgxTask() {
    @get:OutputFile
    val outputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputEnclaveMetadata: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val signTool: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val enclaveletJar: RegularFileProperty = objects.fileProperty()

    override fun sgxAction() {
        val trustKey  = BuildJarObject.readEnclaveClassName(enclaveletJar.asFile.get().toURI().toURL())
        val measurement = getEnclaveMeasurement(outputSignedEnclave.asFile.get())
        logger.lifecycle("Enclave measurement: $measurement")
        generateOutput(outputEnclaveMetadata.asFile.get(), trustKey, measurement)
    }

    private fun getEnclaveMeasurement(file: File): String {
        val metadata = temporaryDir.toPath().resolve("sign_tool_dump.txt").toFile()
        project.exec { spec ->
            spec.commandLine(signTool.asFile.get(), "dump",
                    "-enclave", file,
                    "-dumpfile", metadata)
        }
        val linesIterator = metadata.readLines().iterator()
        while (linesIterator.hasNext()) {
            val line = linesIterator.next()
            if (line.trim(':', '\n', '\r') == "metadata->enclave_css.body.enclave_hash.m") {
                return StringBuilder().apply {
                    for (j in 1..2) {
                        val nextLine = linesIterator.next()
                        append(nextLine.trim(':', '\n', '\r')
                                .split(' ')
                                .filterNot(String::isEmpty)
                                .joinToString("") { it.substring(2) })
                    }
                }.toString()
            }
        }
        throw EOFException("Unable to read enclave measurement from metadata dump file: ${metadata.absolutePath}")
    }

    private fun generateOutput(destination: File, metadataKey: String, measurement: String) {
        destination.outputStream().bufferedWriter().use { outputWriter->
            outputWriter.write("className: $metadataKey")
            outputWriter.newLine()
            outputWriter.write("measurement: $measurement")
            outputWriter.newLine()
        }
    }
}
