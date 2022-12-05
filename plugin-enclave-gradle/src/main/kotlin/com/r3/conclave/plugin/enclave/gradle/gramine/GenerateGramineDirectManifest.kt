package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.plugin.enclave.gradle.BuildType
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.plugin.enclave.gradle.LinuxExec
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


open class GenerateGramineDirectManifest @Inject constructor(
    objects: ObjectFactory,
    private val buildType: BuildType,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "$GRAMINE_MANIFEST.template"
        const val PYTHON_ENCLAVE_SIZE = "8G"
        const val JAVA_ENCLAVE_SIZE = "4G"
    }

    @get:Input
    val productId: Property<Int> = objects.property(Int::class.java)

    @get:Input
    val revocationLevel: Property<Int> = objects.property(Int::class.java)

    @get:Input
    val maxThreads: Property<Int> = objects.property(Int::class.java)

    @get:InputFile
    val signingKey: RegularFileProperty = objects.fileProperty()

    @get:Input
    val pythonEnclave: Property<Boolean> = objects.property(Boolean::class.java)

    @get:Input
    val buildInDocker: Property<Boolean> = objects.property(Boolean::class.java)

    @get:OutputFile
    val manifestFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val manifestTemplateFile = temporaryDir.resolve(MANIFEST_TEMPLATE).toPath()
        javaClass.copyResource(MANIFEST_TEMPLATE, manifestTemplateFile)

        // TODO We generate Gramine manifest for Python enclaves outside the conclave-build container because some
        //  libraries are installed in the user space instead of the system space. Running such enclaves
        //  outside the conclave-build container would fail. Because of that, we are relying on python3, pip3 and jep
        //  to be installed on the local machine. Rather than documenting all this and expecting the user to have their
        //  machine correctly setup, it is better to embed the conclave-build container to always run when building the enclave.
        //  https://r3-cev.atlassian.net/browse/CON-1229

        val gramineManifestArgs = mutableListOf(
            "gramine-manifest",
            "-Djava_home=${System.getProperty("java.home")}",
            "-Disv_prod_id=${productId.get()}",
            "-Disv_svn=${revocationLevel.get() + 1}",
            "-Dis_python_enclave=${pythonEnclave.get()}",
            "-Denclave_mode=${buildType.name.uppercase()}",
            "-Denclave_worker_threads=10",
            "-Dgramine_max_threads=${maxThreads.get()}",
            "-Denclave_size=${if (pythonEnclave.get()) PYTHON_ENCLAVE_SIZE else JAVA_ENCLAVE_SIZE}",
            manifestTemplateFile.absolutePathString(),
            manifestFile.asFile.get().absolutePath
        )

        if (buildType == BuildType.Simulation) {
            val simulationMrSigner = computeSigningKeyMeasurement().toHexString()
            gramineManifestArgs.add("-Dsimulation_mrsigner=$simulationMrSigner")
        }

        /**
            Generate Gramine manifest for Python enclaves outside the conclave-build container.
         */
        if (!buildInDocker.get() || pythonEnclave.get()) {
            val architecture = "x86_64-linux-gnu"
            val ldPreload = executePython(
                "from sysconfig import get_config_var; " +
                        "print(get_config_var('LIBPL') + '/' + get_config_var('LDLIBRARY'))"
            )
            // The location displayed by 'pip3 show jep' is actually of the site/dist-packages dir, not the specific 'jep'
            // dir within it. We assume this is the packages dir for other modules as well. If this assumption is
            // incorrect then we'll need to come up with a better solution.
            val pythonPackagesPath = commandWithOutput("pip3", "show", "jep")
                .splitToSequence("\n")
                .single { it.startsWith("Location: ") }
                .substringAfter("Location: ")

            gramineManifestArgs.add("-Darch_libdir=/lib/$architecture")
            gramineManifestArgs.add("-Dld_preload=$ldPreload")
            gramineManifestArgs.add("-Dpython_packages_path=$pythonPackagesPath")

            commandLine(gramineManifestArgs)
        } else {
            // The location displayed by 'pip3 show jep' is actually of the site/dist-packages dir, not the specific 'jep'
            // dir within it. We assume this is the packages dir for other modules as well. If this assumption is
            // incorrect then we'll need to come up with a better solution.
            val pythonPackagesPath = linuxExec.execWithOutput(listOf("pip3", "show", "jep"))
                .splitToSequence("\n")
                .single { it.startsWith("Location: ") }
                .substringAfter("Location: ")

            gramineManifestArgs.add("-Darch_libdir=")
            gramineManifestArgs.add("-Dld_preload=")
            gramineManifestArgs.add("-Dpython_packages_path=$pythonPackagesPath")
            linuxExec.exec(gramineManifestArgs)
        }
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
