package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import java.io.EOFException
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * This task generates a yaml metadata file containing the enclavelet class name and measurement.
 */
open class GenerateEnclaveMetadata @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val inputSignedEnclave: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputSignTool: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputEnclaveMetadata: RegularFileProperty = objects.fileProperty()

    override fun sgxAction() {
        val measurement = getEnclaveMeasurement(inputSignedEnclave.asFile.get())
        logger.lifecycle("Enclave measurement: $measurement")
        Files.write(
                outputEnclaveMetadata.asFile.get().toPath(),
                listOf("measurement: $measurement")
        )
    }

    private fun getEnclaveMeasurement(file: File): String {
        val metadata = temporaryDir.toPath().resolve("sign_tool_dump.txt").toFile()
        project.exec { spec ->
            spec.commandLine(inputSignTool.asFile.get(), "dump",
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
}
