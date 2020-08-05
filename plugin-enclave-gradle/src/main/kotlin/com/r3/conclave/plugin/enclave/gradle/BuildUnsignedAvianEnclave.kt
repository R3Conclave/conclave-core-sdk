package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class BuildUnsignedAvianEnclave @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
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

    override fun action() {
        project.exec { spec ->
            spec.commandLine(listOf(
                inputLd.get(),
                "-pie", "--entry=enclave_entry",
                "-m", "elf_x86_64",
                // We don't specify -Bsymbolic here, because the behaviour of this flag in combination with the others
                // appears to vary in subtle ways between ld versions causing reproducibility failures, and because it
                // doesn't do anything for enclaves (even though Intel set this option in their samples). All the flag
                // does is make a DT_SYMBOLIC tag appear in the .dynamic section but the Intel urts ELF interpreter
                // doesn't do anything with it - indeed the tag has little meaning in an enclave environment.
                "-Bstatic", "--no-undefined", "--export-dynamic",
                "-o", outputs.files.first())
                + optionsForStripped
                + listOf(inputEnclaveObject.asFile.get(), inputJarObject.asFile.get())
            )
        }
    }
}
