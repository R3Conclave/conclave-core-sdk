package com.r3.conclave.plugin.enclave.gradle

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputFile
import java.io.FileWriter
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.spec.RSAKeyGenParameterSpec
import javax.inject.Inject

open class GenerateDummyMrsignerKey @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:OutputFile
    val outputKey: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val rsaKeyGen = KeyPairGenerator.getInstance("RSA")
        rsaKeyGen.initialize(RSAKeyGenParameterSpec(3072, BigInteger.valueOf(3)))
        val privateKey = rsaKeyGen.generateKeyPair().private
        JcaPEMWriter(FileWriter(outputKey.get().asFile)).use {
            it.writeObject(privateKey)
        }
    }
}
