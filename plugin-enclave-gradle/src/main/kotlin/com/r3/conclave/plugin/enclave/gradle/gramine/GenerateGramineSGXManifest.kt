package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.process.ExecResult
import java.io.File
import javax.inject.Inject

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
        val enclaveDestinationJarName = "${outputSGXManifestPath}/$GRAMINE_ENCLAVE_JAR"
        val enclaveDestinationJarFile = File(enclaveDestinationJarName)
        checkNotNull(
            enclaveJar.get().asFile.copyTo(
                enclaveDestinationJarFile,
                overwrite = true
            )
        ) { "Enclave jar file not copied correctly while builgetEnclaveThreadCountFromManifestding the Gramine SGX manifest" }
        val enclaveDestinationPythonFileName = "${outputSGXManifestPath}/$PYTHON_FILE"
        val enclaveDestinationPythonFile = File(enclaveDestinationPythonFileName)

        if (pythonSourcePath.isPresent) {
            checkNotNull(
                pythonSourcePath.get().asFile.copyTo(
                    enclaveDestinationPythonFile,
                    overwrite = true
                )
            ) { "Python script not copied correctly while building the Gramine SGX manifest" }
        }
        check(signDirectManifest(manifestPath, privateKeyPath).exitValue == 0) { "Could not sign the manifest" }

        if (sgxGetToken().exitValue == 0) {
            throw GradleException("Could not get the token for the SGX manifest")
        }
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