package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import java.io.File
import javax.inject.Inject

open class BuildJarObject @Inject constructor(objects: ObjectFactory) : SgxTask() {
    @get:InputDirectory
    val inputBinutilsDirectory: DirectoryProperty = objects.directoryProperty()

    @get:InputFile
    val inputJar: RegularFileProperty = objects.fileProperty()

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @get:Internal
    var embeddedJarName: String = "app.jar"

    @get:Internal
    var outputName: String = "app.jar.o"

    private val outputJar: File get() = outputDir.file(embeddedJarName).get().asFile

    @get:OutputFile
    val outputJarObject: Provider<RegularFile> = outputDir.file(outputName)

    override fun sgxAction() {
        if (System.getProperty("os.name") != "Linux") {
            throw GradleException("At this time you may only build enclaves on a Linux host. We hope to remove this limitation in a future release of Conclave. Sorry!")
        }

        val binutilsDirectory = inputBinutilsDirectory.asFile.get()
        inputJar.asFile.get().copyTo(outputJar, overwrite = true)
        project.exec { spec ->
            spec.workingDir(outputDir)
            spec.commandLine(
                    File(binutilsDirectory, "ld"),
                    "-r",
                    "-b", "binary",
                    embeddedJarName,
                    "-o", outputJarObject.get().asFile
            )
        }
    }
}
