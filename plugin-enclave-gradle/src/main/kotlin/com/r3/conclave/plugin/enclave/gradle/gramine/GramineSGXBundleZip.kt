package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_TOKEN
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SIGSTRUCT
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import javax.inject.Inject
import kotlin.io.path.copyTo

open class GramineSGXBundleZip @Inject constructor(objects: ObjectFactory) : Zip() {
    @get:InputFile
    val signingKey: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val directManifest: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val enclaveJar: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    val pythonFile: RegularFileProperty = objects.fileProperty()

    @TaskAction
    override fun copy() {
        // Copy everything into the task's temp dir so that Gramine has a consistent working dir and can reference
        // these files using relative paths.
        directManifest.copyToTempDir(GRAMINE_MANIFEST)
        enclaveJar.copyToTempDir(GRAMINE_ENCLAVE_JAR)
        if (pythonFile.isPresent) {
            pythonFile.copyToTempDir(PYTHON_FILE)
        }

        // This will create a .manifest.sgx file into the temp dir
        project.exec { spec ->
            spec.commandLine = listOf(
                "gramine-sgx-sign",
                "--manifest=$GRAMINE_MANIFEST",
                "--key=${signingKey.get().asFile.absolutePath}",
                "--output=$GRAMINE_SGX_MANIFEST"
            )
            spec.workingDir = temporaryDir
        }

        // This will create a .token file into the temp dir
        project.exec { spec ->
            spec.commandLine = listOf(
                "gramine-sgx-get-token",
                "--sig=$GRAMINE_SIGSTRUCT",
                "--output=$GRAMINE_SGX_TOKEN"
            )
            spec.workingDir = temporaryDir
        }

        // Zip up the temp dir, but without the .manifest file as it's no longer needed.
        from(temporaryDir)
        exclude(GRAMINE_MANIFEST)
        super.copy()
    }

    private fun RegularFileProperty.copyToTempDir(fileName: String) {
        get().asFile.toPath().copyTo(temporaryDir.resolve(fileName).toPath())
    }
}
