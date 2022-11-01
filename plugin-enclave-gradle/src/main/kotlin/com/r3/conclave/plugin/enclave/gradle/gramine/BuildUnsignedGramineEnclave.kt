package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.plugin.enclave.gradle.div
import com.r3.conclave.utilities.internal.copyResource
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.toHexString
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.impldep.org.eclipse.jgit.lib.ObjectChecker.`object`
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
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
     */
    private fun generateGramineEnclaveMetadata() {
        /**
         * We don't use the java Properties class here for two reasons:
         * - It doesn't guarantee consistent order.
         * - It prints the date and time at the top of the file.
         */
        val properties = TreeMap<String, String>().apply {
            put("maxThreads", "${maxThreads.get()}")
            put("signingKeyMeasurement", computeSigningKeyMeasurement(inputKey.asFile.get()))
        }

        outputGramineEnclaveMetadata.asFile.get().writer().use { outputStream ->
            for (entry in properties.entries) {
                outputStream.write("${entry.key}=${entry.value}\n")
            }
        }
    }

    /**
     * Compute an SGX-like signing key measurement of the specified key.
     * Output is a lower case hexadecimal string.
     */
    private fun computeSigningKeyMeasurement(keyFile: File): String {
        System.err.println(keyFile)

        val rsaPrivateKey = keyFile.reader().use {
            val pemParser = PEMParser(it)
            val keyConverter = JcaPEMKeyConverter()
            val pemObject = pemParser.readObject()
            val kp: KeyPair = keyConverter.getKeyPair(pemObject as PEMKeyPair)
            kp.private as RSAPrivateKey
        }

        val digest = digest("SHA-256") {
            update(rsaPrivateKey.modulus.toByteArray())
        }

        check(digest.size == 32)

        return digest.toHexString().lowercase()
    }
}
