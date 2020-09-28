package com.r3.conclave.internaltesting.dynamic

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.spec.RSAKeyGenParameterSpec

object SignEnclave {
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
            ProcessRunner.runProcess(
                    commandLine = listOf(
                            signTool.absolutePath, "sign",
                            "-key", key.absolutePath,
                            "-enclave", enclave.absolutePath,
                            "-out", output.absolutePath,
                            "-config", config.absolutePath
                    ),
                    directory = output.parentFile
            )
        }
    }

    fun createDummyKey(input: String? = null): Cached<File> {
        return Cached.singleFile(DigestTools.md5String(input ?: "dummy"), "dummy.key") { output ->
            val rsaKeyGen = KeyPairGenerator.getInstance("RSA")
            rsaKeyGen.initialize(RSAKeyGenParameterSpec(3072, BigInteger.valueOf(3)))
            val privateKey = rsaKeyGen.generateKeyPair().private
            JcaPEMWriter(FileWriter(output)).use {
                it.writeObject(privateKey)
            }
        }
    }

    fun enclaveMetadata(inputEnclave: Cached<File>): Cached<File> {
        return Cached.combineFile(listOf(extractSignTool(), inputEnclave), "metadata.txt") { output, (signTool, enclave) ->
            ProcessRunner.runProcess(
                    listOf(signTool.absolutePath, "dump", "-enclave", enclave.absolutePath, "-dumpfile", output.absolutePath),
                    output.parentFile
            )
        }
    }

    private fun extractSignTool(): Cached<File> {
        return ExtractResource.extractResource(javaClass, "/com/r3/conclave/sign-tool/sgx_sign", "r-xr-xr-x")
    }
}
