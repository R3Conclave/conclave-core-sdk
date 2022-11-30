package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject

open class BuildJarObject @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val inputLd: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputJar: RegularFileProperty = objects.fileProperty()

    /**
     * The path of the output object file both determines the working directory for the ld command and the filename to use
     * for the embedded jar file.
     */
    @get:OutputFile
    val outputJarObject: RegularFileProperty = objects.fileProperty()

    override fun action() {
        if (!OperatingSystem.current().isLinux && !OperatingSystem.current().isWindows && !OperatingSystem.current().isMacOsX) {
            throw GradleException("At this time you may only build enclaves on a Linux, Windows or macOS host.")
        }

        val workingDirectory = outputJarObject.asFile.get().parent
        val embeddedJarName = outputJarObject.asFile.get().name.removeSuffix(".o")

        project.copy { spec ->
            spec.from(inputJar)
            spec.into(workingDirectory)
            spec.rename { embeddedJarName }
        }

        commandLine(
                inputLd.get(),
                "-r",
                "-b", "binary",
                "-m", "elf_x86_64",
                embeddedJarName,
                "-o", outputJarObject.get().asFile,
                commandLineConfig = CommandLineConfig(workingDirectory)
        )
    }
}
