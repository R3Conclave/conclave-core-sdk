package com.r3.conclave.dynamictesting

import java.io.ByteArrayOutputStream
import java.io.File

object SignEnclave {
    fun extractSignTool(): Cached<File> {
        return ExtractResource.extractResource(javaClass, "/com/r3/conclave/sign-tool/sgx_sign", "r-xr-xr-x")
    }

    fun createConfig(enclaveConfig: EnclaveConfig): Cached<File> {
        val byteStream = ByteArrayOutputStream()
        enclaveConfig.marshal(byteStream)
        val bytes = byteStream.toByteArray()
        return Cached.singleFile(DigestTools.md5ByteArray(bytes), "enclave.xml") { output ->
            output.writeBytes(bytes)
        }
    }

    fun signEnclave(inputKey: Cached<File>, inputEnclave: Cached<File>, enclaveConfig: Cached<File>): Cached<File> {
        return Cached.combineFile(listOf(extractSignTool(), inputKey, inputEnclave, enclaveConfig), "enclave.signed.so") { output, (signTool, key, enclave, config) ->
            ProcessRunner.runProcess(signEnclaveCommandLine(signTool, key, enclave, config, output), output.parentFile)
        }
    }

    private fun signEnclaveCommandLine(signTool: File, inputKey: File, inputEnclave: File, inputEnclaveConfig: File, outputSignedEnclave: File): List<String> {
        return listOf(
                signTool.absolutePath, "sign",
                "-key", inputKey.absolutePath,
                "-enclave", inputEnclave.absolutePath,
                "-out", outputSignedEnclave.absolutePath,
                "-config", inputEnclaveConfig.absolutePath
        )
    }

    fun createDummyKey(input: String? = null): Cached<File> {
        return Cached.singleFile(DigestTools.md5String(input ?: "dummy"), "dummy.key") { output ->
            ProcessRunner.runProcess(createDummyKeyCommandLine(output), output.parentFile)
        }
    }

    private fun createDummyKeyCommandLine(outputKey: File): List<String> {
        return listOf("/usr/bin/env", "openssl", "genrsa", "-out", outputKey.absolutePath, "-3", "3072")
    }

    fun enclaveMetadata(inputEnclave: Cached<File>): Cached<File> {
        return Cached.combineFile(listOf(extractSignTool(), inputEnclave), "metadata.txt") { output, (signTool, enclave) ->
            ProcessRunner.runProcess(
                    listOf(signTool.absolutePath, "dump", "-enclave", enclave.absolutePath, "-dumpfile", output.absolutePath),
                    output.parentFile
            )
        }
    }
}
