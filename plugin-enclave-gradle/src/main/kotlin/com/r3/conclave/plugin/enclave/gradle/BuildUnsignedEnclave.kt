package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class BuildUnsignedEnclave @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val inputLd: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputJarObject: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveObject: RegularFileProperty = objects.fileProperty()

    @get:Input
    val stripped: Property<Boolean> = objects.property(Boolean::class.javaObjectType)

    @get:OutputFile
    val outputEnclave: RegularFileProperty = objects.fileProperty()

    private val optionsForStripped: List<String> get() = if (stripped.getOrElse(false)) {
        // For releases, we also need to remove all symbols
        // so that the output artifact is repeatable.
        listOf("--strip-all")
    } else {
        emptyList()
    }

    override fun sgxAction() {
        project.exec { spec ->
            spec.commandLine(listOf(
                inputLd.get(),
                "-pie", "--entry=enclave_entry",
                "-Bstatic", "-Bsymbolic", "--no-undefined", "--export-dynamic",
                "-o", outputEnclave.asFile.get())
                + optionsForStripped
                + listOf(inputEnclaveObject.asFile.get(), inputJarObject.asFile.get())
            )
        }
    }
}
