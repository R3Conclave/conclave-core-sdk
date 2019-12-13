package com.r3.sgx.dynamictesting

import java.io.ByteArrayOutputStream
import java.io.File

object SignEnclave {
    fun getSignToolResponcePath(): String {
        return "/com/r3/sgx/sign-tool/sgx_sign"
    }

    fun extractSignTool(): Cached<File> {
        return ExtractResource.extractResource(javaClass, getSignToolResponcePath(), "r-xr-xr-x")
    }

    fun signEnclaveCommandLine(signTool: File, inputKey: File, inputEnclave: File, inputEnclaveConfig: File, outputSignedEnclave: File): List<String> {
        return listOf(signTool.absolutePath, "sign",
                "-key", inputKey.absolutePath,
                "-enclave", inputEnclave.absolutePath,
                "-out", outputSignedEnclave.absolutePath,
                "-config", inputEnclaveConfig.absolutePath
        )
    }

    fun createDummyKeyCommandLine(outputKey: File): List<String> {
        return listOf("/usr/bin/env", "openssl", "genrsa", "-out", outputKey.absolutePath, "-3", "3072")
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

    fun createDummyKey(): Cached<File> {
        return Cached.singleFile(DigestTools.md5String("dummy"), "dummy.key") { output ->
            ProcessRunner.runProcess(createDummyKeyCommandLine(output), output.parentFile)
        }
    }
}
