package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.BuildType
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.plugin.enclave.gradle.div
import com.r3.conclave.utilities.internal.copyResource
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
import java.nio.file.Path
import java.nio.file.Paths
import java.security.interfaces.RSAPublicKey
import java.util.*
import javax.inject.Inject
import kotlin.io.path.absolutePathString


open class BuildUnsignedGramineEnclave @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "java.manifest.template"
        const val MANIFEST_DIRECT = "java.manifest"
        const val METADATA_DIRECT = "enclave-metadata.properties"
        const val GRAMINE_MANIFEST_EXECUTABLE = "gramine-manifest"
    }

    @Input
    val entryPoint: Property<String> = objects.property(String::class.java)

    @Input
    val archLibDirectory: Property<String> = objects.property(String::class.java)

    @Input
    val buildDirectory: Property<String> = objects.property(String::class.java)

    @Input
    val maxThreads: Property<Int> = objects.property(Int::class.java)

    @Input
    val buildType: Property<BuildType> = objects.property(BuildType::class.java)

    @InputFile
    val inputKey: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputManifest: RegularFileProperty = objects.fileProperty()

    @get:OutputFile
    val outputGramineEnclaveMetadata: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val templateManifest = copyTemplateManifestToBuildDirectory()
        generateGramineDirectManifest(templateManifest.absolutePathString())
        generateGramineEnclaveMetadata()
    }

    private fun copyTemplateManifestToBuildDirectory(): Path {
        val outputManifestTemplateFile = Paths.get(buildDirectory.get()) / MANIFEST_TEMPLATE
        BuildUnsignedGramineEnclave::class.java.copyResource(MANIFEST_TEMPLATE, outputManifestTemplateFile)
        return outputManifestTemplateFile
    }

    private fun generateGramineDirectManifest(templateManifest: String) {
        /**
         * It's possible for Gramine to launch threads internally that conclave won't know about!
         * Because of this, we need to add some safety margin.
         */
        val gramineThreadCount = maxThreads.get() * 2
        val command = listOf(
            GRAMINE_MANIFEST_EXECUTABLE,
            "-Darch_libdir=${archLibDirectory.get()}",
            "-Dentrypoint=${entryPoint.get()}",
            "-DmaxThreads=$gramineThreadCount",
            templateManifest,
            outputManifest.asFile.get().absolutePath
        )
        commandLine(command)
    }

    /**
     * Generate metadata for use in simulation mode.
     * The metadata file is a properties file which is packaged along with the manifest.
     * This file contains data that the host needs to know in order to load the enclave.
     */
    private fun generateGramineEnclaveMetadata() {
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
            if (isSimulation) {
                put("signingKeyMeasurement", Base64.getEncoder().encodeToString(computeMrsigner(inputKey.asFile.get())))
            }
        }

        outputGramineEnclaveMetadata.asFile.get().writer().use { outputStream ->
            for (entry in properties.entries) {
                outputStream.write("${entry.key}=${entry.value}\n")
            }
        }
    }

    /** Compute the mrsigner value from a provided .pem file containing a 3072 bit RSA key. */
    private fun computeMrsigner(keyFile: File): ByteArray {
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

        val modulusBytes = key.modulus.toByteArray()

        /**
         * Check the key length by checking the modulus.
         * Modulus is 385 bytes rather than 384 (3072 / 8) due to two's complement.
         */
        check(modulusBytes.size == 385) { "Signing key must be a 3072 bit RSA key." }

        /**
         * The modulus bytes are a big-endian representation.
         * Here we do a quick sanity check to ensure that the MSB (which would contain the sign bit) really is zero.
         */
        check(modulusBytes[0] == 0.toByte())

        /** Reverse the bytes (currently big endian, need little endian), then compute the measurement. */
        return digest("SHA-256") {
            modulusBytes.reverse()
            update(modulusBytes, 0, 384)    // Ignore the 385th byte.
        }
    }
}
