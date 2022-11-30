package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_TOKEN
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SIGSTRUCT
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
import com.r3.conclave.plugin.enclave.gradle.BuildType
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.utilities.internal.copyResource
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.toHexString
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.deleteExisting

open class GenerateGramineBundle @Inject constructor(
    objects: ObjectFactory,
    private val buildType: BuildType
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

    @get:InputFile
    val enclaveJar: RegularFileProperty = objects.fileProperty()

    @get:InputFile
    @get:Optional
    val pythonFile: RegularFileProperty = objects.fileProperty()

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    override fun action() {
        val manifestTemplateFile = temporaryDir.resolve(MANIFEST_TEMPLATE).toPath()
        javaClass.copyResource(MANIFEST_TEMPLATE, manifestTemplateFile)

        enclaveJar.copyToOutputDir(GRAMINE_ENCLAVE_JAR)
        if (pythonFile.isPresent) {
            pythonFile.copyToOutputDir(PYTHON_FILE)
        }

        // TODO We're relying on gcc, python3, pip3 and jep being installed on the machine that builds the Python
        //  enclave. Rather than documenting all this and expecting the user to have their machine correctly setup, it
        //  is better to embed the conclave-build container to always run when building the enclave, not just for
        //  non-linux. https://r3-cev.atlassian.net/browse/CON-1181

        val architecture = commandWithOutput("gcc", "-dumpmachine").trimEnd()
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

        project.exec { spec ->
            val command = mutableListOf(
                "gramine-manifest",
                "-Djava_home=${System.getProperty("java.home")}",
                "-Darch_libdir=/lib/$architecture",
                "-Dld_preload=$ldPreload",
                "-Disv_prod_id=${productId.get()}",
                "-Disv_svn=${revocationLevel.get() + 1}",
                "-Dpython_packages_path=$pythonPackagesPath",
                "-Dis_python_enclave=${pythonFile.isPresent}",
                "-Denclave_mode=${buildType.name.uppercase()}",
                "-Denclave_worker_threads=10",
                "-Dgramine_max_threads=${maxThreads.get()}",
                "-Denclave_size=${if (pythonFile.isPresent) PYTHON_ENCLAVE_SIZE else JAVA_ENCLAVE_SIZE}",
                manifestTemplateFile.absolutePathString(),
                GRAMINE_MANIFEST
            )
            if (buildType == BuildType.Simulation) {
                val simulationMrSigner = computeSigningKeyMeasurement().toHexString()
                command += "-Dsimulation_mrsigner=$simulationMrSigner"
            }
            spec.commandLine = command
            spec.setWorkingDir(outputDir)
        }

        if (buildType != BuildType.Simulation) {
            // This will create a .manifest.sgx and a .sig files into the output dir
            project.exec { spec ->
                spec.commandLine = listOf(
                    "gramine-sgx-sign",
                    "--manifest=$GRAMINE_MANIFEST",
                    "--key=${signingKey.get().asFile.absolutePath}",
                    "--output=$GRAMINE_SGX_MANIFEST"
                )
                spec.setWorkingDir(outputDir)
            }

            // This will create a .token file into the output dir
            project.exec { spec ->
                spec.commandLine = listOf(
                    "gramine-sgx-get-token",
                    "--sig=$GRAMINE_SIGSTRUCT",
                    "--output=$GRAMINE_SGX_TOKEN"
                )
                spec.setWorkingDir(outputDir)
            }

            // The .manifest is not needed for debug and release modes
            outputDir.file(GRAMINE_MANIFEST).get().asFile.toPath().deleteExisting()
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
         * toByteArray() has an extra byte for the sign, thus making 385 rather than 384 (3072 / 8).
         */
        // TODO toByteArray() returns the minimum number of bytes for the representation, and so this may be less
        //  than 385 bytes
        check(modulusBytes.size == 385) { "Signing key must be a 3072 bit RSA key." }

        return digest("SHA-256") {
            update(modulusBytes, 0, 384)    // Ignore the sign byte.
        }
    }

    private fun executePython(command: String): String = commandWithOutput("python3", "-c", command).trimEnd()

    private fun RegularFileProperty.copyToOutputDir(fileName: String) {
        get().asFile.toPath().copyTo(outputDir.file(fileName).get().asFile.toPath(), REPLACE_EXISTING)
    }
}
