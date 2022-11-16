package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.process.ExecResult
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.io.path.copyTo

open class GenerateGramineSGXManifest @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val GRAMINE_SGX_SIGN_EXECUTABLE = "gramine-sgx-sign"
        const val GRAMINE_GET_TOKEN_EXECUTABLE = "gramine-sgx-get-token"
    }

    @get:InputFile
    val privateKey: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val directManifest: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val enclaveJar: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @Optional
    val pythonSourcePath: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val sgxManifest: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val sgxToken: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val sgxSig: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val manifestPath = directManifest.get().asFile.absolutePath
        val privateKeyPath = privateKey.get().asFile.absolutePath
        val outputSGXManifestPath = sgxManifest.asFile.get().parentFile.absolutePath
        val enclaveDestinationJarPath = Paths.get("${outputSGXManifestPath}/$GRAMINE_ENCLAVE_JAR")
        enclaveJar.get().asFile.toPath().copyTo(enclaveDestinationJarPath)
        val enclaveDestinationPythonPath = Paths.get("${outputSGXManifestPath}/$PYTHON_FILE")

        if (pythonSourcePath.isPresent) {
            pythonSourcePath.get().asFile.toPath().copyTo(enclaveDestinationPythonPath)
        }

        signDirectManifest(manifestPath, privateKeyPath)
        sgxGetToken()
    }

    private fun signDirectManifest(directManifest: String, privateKey: String): ExecResult {
        val command = listOf(
            GRAMINE_SGX_SIGN_EXECUTABLE,
            "--manifest=$directManifest",
            "--key=$privateKey",
            "--output=${sgxManifest.asFile.get().absolutePath}"
        )
        val enclaveJarBuildDir = sgxManifest.asFile.get().parentFile.absolutePath
        return commandLine(command, commandLineConfig = CommandLineConfig(workingDir = enclaveJarBuildDir))
    }

    private fun sgxGetToken(): ExecResult {
        val command = listOf(
            GRAMINE_GET_TOKEN_EXECUTABLE,
            "--sig=${sgxSig.get().asFile.absolutePath}",
            "--output=${sgxToken.asFile.get().absolutePath}"
        )
        return commandLine(command)
    }
}
