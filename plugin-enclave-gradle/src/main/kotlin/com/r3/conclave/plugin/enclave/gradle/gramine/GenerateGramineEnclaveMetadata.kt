package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.BuildType
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.utilities.internal.digest
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import java.io.File
import java.security.interfaces.RSAPublicKey
import java.util.*
import javax.inject.Inject

/**
 * This task generates a properties file containing metadata used by the host to launch a gramine enclave.
 * It is also used in simulation mode to pass a mock mrsigner value to the enclave.
 */
open class GenerateGramineEnclaveMetadata @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val METADATA_FILE_NAME = "enclave-metadata.properties"
    }

    @get:Input
    val buildType: Property<BuildType> = objects.property(BuildType::class.java)
    @get:Input
    val maxThreads: Property<Int> = objects.property(Int::class.java)
    @get:InputFile
    val signingKey: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputGramineEnclaveMetadata: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val isSimulation = buildType.get() == BuildType.Simulation

        /**
         * We don't use the java Properties class here for two reasons:
         * - It doesn't guarantee consistent order.
         * - It prints the date and time at the top of the file.
         */
        val properties = TreeMap<String, String>().apply {
            put("isSimulation", isSimulation.toString())
            put("maxThreads", maxThreads.get().toString())

            /** The signing key measurement should not be included in modes other than simulation. */
            // TODO: CON-1163 you will probably need to remove this for mock attestation in debug/release mode!
            if (isSimulation) {
                val mrsigner = computeSigningKeyMeasurement(signingKey.asFile.get())
                put("signingKeyMeasurement", Base64.getEncoder().encodeToString(mrsigner))
            }
        }

        outputGramineEnclaveMetadata.asFile.get().writer().use { writer ->
            for (entry in properties.entries) {
                writer.write("${entry.key}=${entry.value}\n")
            }
        }
    }

    /** Compute the mrsigner value from a provided .pem file containing a 3072 bit RSA key. */
    private fun computeSigningKeyMeasurement(keyFile: File): ByteArray {
        /**
         * Doesn't actually matter if we use the public or private key here.
         * We only care about the modulus (which is the same for either).
         */
        val key = keyFile.reader().use {
            val pemParser = PEMParser(it)
            val keyConverter = JcaPEMKeyConverter()
            val pemObject = pemParser.readObject()
            val keyPair = keyConverter.getKeyPair(pemObject as PEMKeyPair)
            keyPair.public as RSAPublicKey
        }

        /**
         * BigInteger.toByteArray() returns a big-endian representation of the modulus. But we need a
         * little-endian representation, so we reverse the bytes here.
         */
        val modulusBytes = key.modulus.toByteArray().apply { reverse() }

        /**
         * Check the key length by checking the modulus.
         * Due to two's complement, the modulus representation is 385 bytes long rather than 384 (3072 / 8).
         */
        check(modulusBytes.size == 385) { "Signing key must be a 3072 bit RSA key." }

        /** Do a quick sanity check to ensure that the most significant byte (two's complement sign) is zero. */
        check(modulusBytes.last() == 0.toByte())

        return digest("SHA-256") {
            update(modulusBytes, 0, 384)    // Ignore the empty sign byte.
        }
    }
}
