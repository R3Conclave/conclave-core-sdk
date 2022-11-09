package com.r3.conclave.plugin.enclave.gradle.graalvm

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject
import kotlin.io.path.createDirectories

open class CopyGraalVM @Inject constructor(objects: ObjectFactory) : Exec() {
    @get:Input
    val configuration: Property<Configuration> = objects.property(Configuration::class.java)

    @get:OutputDirectory
    val distributionDir: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    override fun exec() {
        logger.info("Copying graalvm.tar.gz ...")
        val graalvmTarGz = configuration.get().files.single { it.name.endsWith("tar.gz") }.absolutePath
        val workingDir = distributionDir.get()
        setWorkingDir(workingDir)
        logger.info("Extracting graalvm.tar.gz into $workingDir ...")
        workingDir.asFile.toPath().createDirectories()
        commandLine("tar", "xf", graalvmTarGz)
        super.exec()
    }
}
