package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.BuildType
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
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
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class GenerateGramineManifest @Inject constructor(
        objects: ObjectFactory,
        private val buildType: BuildType
) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "java.manifest.template"
    }

    @get:Input
    val maxThreads: Property<Int> = objects.property(Int::class.java)
    @get:InputFile
    val signingKey: RegularFileProperty = objects.fileProperty()
    @get:Input
    val pythonEnclave: Property<Boolean> = objects.property(Boolean::class.java)

    @get:OutputFile
    val manifestFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val manifestTemplateFile = temporaryDir.resolve(MANIFEST_TEMPLATE).toPath()
        javaClass.copyResource(MANIFEST_TEMPLATE, manifestTemplateFile)

        // TODO We're relying on gcc, python3, pip3 and jep being installed on the machine that builds the Python
        //  enclave. Rather than documenting all this and expecting the user to have their machine correctly setup, it
        //  is better to embed the conclave-build container to always run when building the enclave, not just for
        //  non-linux. https://r3-cev.atlassian.net/browse/CON-1181

        val architecture = commandWithOutput("gcc", "-dumpmachine").trimEnd()
        val ldPreload = executePython("from sysconfig import get_config_var; " +
                "print(get_config_var('LIBPL') + '/' + get_config_var('LDLIBRARY'))"
        )
        // The location displayed by 'pip3 show jep' is actually of the site/dist-packages dir, not the specific 'jep'
        // dir within it. We assume this is the packages dir for other modules as well. If this assumption is
        // incorrect then we'll need to come up with a better solution.
        val pythonPackagesPath = commandWithOutput("pip3", "show", "jep")
            .splitToSequence("\n")
            .single { it.startsWith("Location: ") }
            .substringAfter("Location: ")

        /**
         * It's possible for a Gramine enclave to launch threads internally that Conclave won't know about!
         * Because of this, we need to add some safety margin.
         */
        val enclaveWorkerThreadCount = maxThreads.get()
        val gramineMaxThreads = enclaveWorkerThreadCount + 8

        commandLine(
            listOf(
                "gramine-manifest",
                "-Djava_home=${System.getProperty("java.home")}",
                "-Darch_libdir=/lib/$architecture",
                "-Dld_preload=$ldPreload",
                "-Dpython_packages_path=$pythonPackagesPath",
                "-Dis_python_enclave=${pythonEnclave.get()}",
                "-Dis_simulation_enclave=${buildType == BuildType.Simulation}",
                "-Dsimulation_mrsigner=${computeSigningKeyMeasurement().toHexString()}",
                "-Denclave_worker_threads=$enclaveWorkerThreadCount",
                "-Dgramine_max_threads=$gramineMaxThreads",
                manifestTemplateFile.absolutePathString(),
                manifestFile.asFile.get().absolutePath
            )
        )
    }

    /**
     * Compute the mrsigner value from a provided .pem file containing a 3072 bit RSA key.
     * This function precisely matches the behaviour of the SGX signing key measurement algorithm.
     */
    private fun computeSigningKeyMeasurement(): ByteArray {
        /**
         * Doesn't actually matter if we use the public or private key here.
         * We only care about the modulus (which is the same for either).
         */
        val key = signingKey.asFile.get().reader().use {
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

    private fun executePython(command: String): String = commandWithOutput("python3", "-c", command).trimEnd()
}
