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
import java.io.File
import javax.inject.Inject

open class GenerateGramineSGXManifest @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val GRAMINE_SGX_SIGN_EXECUTABLE = "gramine-sgx-sign"
        const val GRAMINE_GET_TOKEN_EXECUTABLE = "gramine-sgx-get-token"
    }

    @get:InputFile
    val inputPrivateKey: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputDirectManifest: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    val inputEnclaveJar: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @Optional
    val inputPythonSourcePath: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputSGXManifest: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputToken: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputSig: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val manifestPath = inputDirectManifest.get().asFile.absolutePath
        val privateKeyPath = inputPrivateKey.get().asFile.absolutePath
        val outputSGXManifestPath = outputSGXManifest.asFile.get().parentFile.absolutePath
        val enclaveDestinationJarName = "${outputSGXManifestPath}/$GRAMINE_ENCLAVE_JAR"
        val enclaveDestinationJarFile = File(enclaveDestinationJarName)
        checkNotNull(
            inputEnclaveJar.get().asFile.copyTo(
                enclaveDestinationJarFile,
                overwrite = true
            )
        ) { "Enclave jar file not copied correctly while building the Gramine SGX manifest" }
        val enclaveDestinationPythonFileName = "${outputSGXManifestPath}/$PYTHON_FILE"
        val enclaveDestinationPythonFile = File(enclaveDestinationPythonFileName)

        if (inputPythonSourcePath.isPresent) {
            inputPythonSourcePath.get().asFile.copyTo(
                enclaveDestinationPythonFile,
                overwrite = true
            )
        } else {
            File.createTempFile("dummy", "dummy") .copyTo(
                enclaveDestinationPythonFile,
                overwrite = true
            )
        }
        check(signDirectManifest(manifestPath, privateKeyPath).exitValue == 0) { "Could not sign the manifest" }
        check(sgxGetToken().exitValue == 0) { "Could not get the token for the SGX manifest" }
    }

    private fun signDirectManifest(directManifest: String, privateKey: String): ExecResult {
        val command = listOf(
            GRAMINE_SGX_SIGN_EXECUTABLE,
            "--manifest=$directManifest",
            "--key=$privateKey",
            "--output=${outputSGXManifest.asFile.get().absolutePath}"
        )
        val enclaveJarBuildDir = outputSGXManifest.asFile.get().parentFile.absolutePath
        return commandLine(command, commandLineConfig = CommandLineConfig(workingDir = enclaveJarBuildDir))
    }

    private fun sgxGetToken(): ExecResult {
        val command = listOf(
            GRAMINE_GET_TOKEN_EXECUTABLE,
            "--sig=${outputSig.get().asFile.absolutePath}",
            "--output=${outputToken.asFile.get().absolutePath}"
        )
        return commandLine(command)
    }
}
