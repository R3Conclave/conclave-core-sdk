package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.common.EnclaveMode
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_ENCLAVE_JAR
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_MANIFEST
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SGX_TOKEN
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_SIGSTRUCT
import com.r3.conclave.common.internal.PluginUtils.PYTHON_FILE
import com.r3.conclave.common.internal.PluginUtils.JLINK_CUSTOM_JDK_DIRECTORY
import com.r3.conclave.common.internal.PluginUtils.SYSTEM_LIB_DIRECTORY
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.plugin.enclave.gradle.LinuxExec
import com.r3.conclave.utilities.internal.copyResource
import com.r3.conclave.utilities.internal.digest
import com.r3.conclave.utilities.internal.toHexString
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
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
import shadow.org.apache.commons.io.IOUtils
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.interfaces.RSAPublicKey
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import kotlin.io.path.*


open class GenerateGramineBundle @Inject constructor(
    objects: ObjectFactory,
    private val enclaveMode: EnclaveMode,
    private val linuxExec: LinuxExec
) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "$GRAMINE_MANIFEST.template"
        const val PYTHON_ENCLAVE_SIZE = "8G"
        const val JAVA_ENCLAVE_SIZE = "4G"
        const val DOCKER_IMAGE_ARCHITECTURE = "x86_64-linux-gnu"
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

    @get:Input
    val extraJavaModules: Property<String> = objects.property(String::class.java)

    @get:InputFile
    @get:Optional
    val pythonFile: RegularFileProperty = objects.fileProperty()

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    override fun action() {
        enclaveJar.copyToOutputDir(GRAMINE_ENCLAVE_JAR)

        val manifestTemplatePath = temporaryDir.resolve(MANIFEST_TEMPLATE).toPath()
        javaClass.copyResource(MANIFEST_TEMPLATE, manifestTemplatePath)

        if (pythonFile.isPresent) {
            generateManifestForPythonEnclaves(manifestTemplatePath)
        } else {
            prepareBundleForJavaEnclaves(manifestTemplatePath)
        }

        if (enclaveMode != EnclaveMode.SIMULATION) {
            generateSgxManifestAndSigstruct()
            generateToken()
            // The .manifest is not needed for debug and release modes
            outputDir.file(GRAMINE_MANIFEST).get().asFile.toPath().deleteExisting()
        }

        if (!pythonFile.isPresent) {
            deleteJavaSystemFiles()
        }
    }

    private fun deleteJavaSystemFiles() {
        listOf(JLINK_CUSTOM_JDK_DIRECTORY, SYSTEM_LIB_DIRECTORY).forEach { it ->
            outputDir.get().asFile.toPath().resolve(it).toFile().deleteRecursively()
        }
    }

    private fun prepareBundleForJavaEnclaves(manifestTemplatePath: Path) {
        val jlinkOutputPath = outputDir.get().asFile.toPath().resolve(JLINK_CUSTOM_JDK_DIRECTORY)

        if (jlinkOutputPath.exists()) {
            jlinkOutputPath.toFile().deleteRecursively()
        }
        createCustomJDK(jlinkOutputPath)

        val outputSystemDir = outputDir.get().asFile.toPath().resolve(SYSTEM_LIB_DIRECTORY)

        if (outputSystemDir.exists()) {
            outputSystemDir.toFile().deleteRecursively()
        }
        copyAdditionalSystemFiles(outputSystemDir)
        generateManifestForJavaEnclaves(manifestTemplatePath)
        compressFiles(jlinkOutputPath, outputSystemDir)
    }

    private fun compressFiles(jlinkOutputPath: Path, outputSystemDir: Path) {
        createTarFile("$jlinkOutputPath")
        createTarFile("$outputSystemDir")
    }

    private fun addFilesToTarGZ(filePath: String, parent: String, tarArchive: TarArchiveOutputStream) {
        val file = File(filePath)
        val entryName = parent + file.name
        tarArchive.putArchiveEntry(TarArchiveEntry(file, entryName))
        if (file.isFile) {
            val fis = FileInputStream(file)
            val bis = BufferedInputStream(fis)

            IOUtils.copy(bis, tarArchive)
            tarArchive.closeArchiveEntry()
            bis.close()
        } else if (file.isDirectory) {
            tarArchive.closeArchiveEntry()

            if (file.listFiles() != null) {
                for (f in file.listFiles()!!) {
                    addFilesToTarGZ(f.absolutePath, entryName + File.separator, tarArchive)
                }
            }
        }
    }

    private fun createTarFile(sourceDir: String) {
        var tarOs: TarArchiveOutputStream? = null
        try {
            val source = File(sourceDir)
            val fos = FileOutputStream(source.absolutePath + ".tar.gz")
            val gos = GZIPOutputStream(BufferedOutputStream(fos))
            tarOs = TarArchiveOutputStream(gos)
            addFilesToTarGZ(sourceDir, "", tarOs)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                tarOs!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun findJavaModules(): String {
        val enclaveJar = outputDir.file(GRAMINE_ENCLAVE_JAR).get().asFile.absolutePath

        val dependentModules = execCommand(
            "jdeps",
            "--print-module-deps",
            "--ignore-missing-deps",
            enclaveJar
        ).run { trimEnd().split(",") }
        val modules = getExtraModules()
        return (modules + dependentModules).distinct().joinToString(separator = ",")
    }

    private fun getExtraModules(): List<String> {
        return if (extraJavaModules.get().isNotEmpty()) {
            extraJavaModules.get().lowercase().replace(" ", "").split(",")
        } else {
            emptyList()
        }
    }

    private fun createCustomJDK(jlinkOutputPath: Path) {
        val modules = findJavaModules()
        execCommand(
            "jlink",
            "--strip-native-debug-symbols", "objcopy=/usr/bin/objcopy",
            "--no-header-files",
            "--no-man-pages",
            "--add-modules", modules,
            "--output", jlinkOutputPath.absolutePathString()
        )
    }

    private fun copyAdditionalSystemFiles(outputSystemDir: Path) {
        //  These files need to be available when running java inside Gramine (leveraging LD_LIBRARY_PATH)
        //  together with the OS files which are provided inside Gramine itself.
        val systemFiles = listOf(
            "/usr/lib/x86_64-linux-gnu/libz.so.1",
            "/usr/lib/x86_64-linux-gnu/libstdc++.so.6",
            "/usr/lib/x86_64-linux-gnu/libgcc_s.so.1"
        )
        systemFiles.forEach {
            val filePath = Paths.get(outputSystemDir.absolutePathString() + it)
            filePath.parent.run {
                if (!exists()) {
                    createDirectories()
                }
            }
            Paths.get(it).copyTo(filePath)
        }
    }

    private fun generateManifestForPythonEnclaves(manifestTemplatePath: Path) {
        // We currently build Python enclaves outside of the conclave-build container
        // TODO: CON-1215 - Building enclaves with Python inside a Docker container
        pythonFile.copyToOutputDir(PYTHON_FILE)

        val architecture = execCommand("gcc", "-dumpmachine")
        val pythonLdPreload = executePython(
            "from sysconfig import get_config_var; " +
                    "print(get_config_var('LIBPL') + '/' + get_config_var('LDLIBRARY'))"
        )
        // The location displayed by 'pip3 show jep' is actually of the site/dist-packages dir, not the specific 'jep'
        // dir within it. We assume this is the packages dir for other modules as well. If this assumption is
        // incorrect then we'll need to come up with a better solution.
        val pythonPackagesPath = execCommand("pip3", "show", "jep")
            .splitToSequence(System.lineSeparator())
            .single { it.startsWith("Location: ") }
            .substringAfter("Location: ")
        val command = prepareManifestGenerationCommand(
            architecture,
            pythonLdPreload,
            pythonPackagesPath,
            System.getProperty("java.home"),
            manifestTemplatePath
        )
        execCommand(*command.toTypedArray())
    }

    private fun generateManifestForJavaEnclaves(manifestTemplatePath: Path) {
        val command = prepareManifestGenerationCommand(
            DOCKER_IMAGE_ARCHITECTURE,
            "",
            "",
            JLINK_CUSTOM_JDK_DIRECTORY,
            manifestTemplatePath
        )
        execCommand(*command.toTypedArray())
    }

    private fun prepareManifestGenerationCommand(
        architecture: String,
        ldPreload: String,
        pythonPackagesPath: String,
        javaHomePath: String,
        manifestTemplate: Path
    ): MutableList<String> {
        // Note that, when running Java enclaves, "java_home" is a relative path (as we are creating the JDK
        //   with jlink) but, when running Python enclaves, it is an absolute path.
        val command = mutableListOf(
            "gramine-manifest",
            "-Djava_home=$javaHomePath",
            "-Darch_libdir=/lib/$architecture",
            "-Dcustom_system_libdir=$SYSTEM_LIB_DIRECTORY/",
            "-Dld_preload=$ldPreload",
            "-Disv_prod_id=${productId.get()}",
            "-Disv_svn=${revocationLevel.get() + 1}",
            "-Dpython_packages_path=$pythonPackagesPath",
            "-Dis_python_enclave=${pythonFile.isPresent}",
            "-Denclave_mode=$enclaveMode",
            "-Denclave_worker_threads=10",
            "-Dgramine_max_threads=${maxThreads.get()}",
            "-Denclave_size=${if (pythonFile.isPresent) PYTHON_ENCLAVE_SIZE else JAVA_ENCLAVE_SIZE}",
            manifestTemplate.absolutePathString(),
            GRAMINE_MANIFEST
        )
        if (enclaveMode == EnclaveMode.SIMULATION) {
            val simulationMrSigner = computeSigningKeyMeasurement().toHexString()
            command += "-Dsimulation_mrsigner=$simulationMrSigner"
        }
        return command
    }

    /**
     * This will create a .manifest.sgx and a .sig files into the output dir.
     */
    private fun generateSgxManifestAndSigstruct() {
        execCommand(
            "gramine-sgx-sign",
            "--manifest=${GRAMINE_MANIFEST}",
            "--key=${signingKey.get().asFile.absolutePath}",
            "--output=${GRAMINE_SGX_MANIFEST}"
        )
    }

    /**
     * This will create a .token file into the output dir
     */
    private fun generateToken() {
        execCommand(
            "gramine-sgx-get-token",
            "--sig=$GRAMINE_SIGSTRUCT",
            "--output=${outputDir.file(GRAMINE_SGX_TOKEN).get().asFile.absolutePath}"
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
         * toByteArray() has an extra byte for the sign, thus making 385 rather than 384 (3072 / 8).
         */
        // TODO toByteArray() returns the minimum number of bytes for the representation, and so this may be less
        //  than 385 bytes
        check(modulusBytes.size == 385) { "Signing key must be a 3072 bit RSA key." }

        return digest("SHA-256") {
            update(modulusBytes, 0, 384)    // Ignore the sign byte.
        }
    }

    private fun execCommand(vararg command: String): String {
        return if (pythonFile.isPresent) {
            commandWithOutput(command = command, workingDir = outputDir.get().asFile.absolutePath)
        } else {
            linuxExec.exec(command.asList(), listOf("-w", outputDir.get().asFile.absolutePath))
        }
    }

    private fun executePython(command: String): String = execCommand("python3", "-c", command)

    private fun RegularFileProperty.copyToOutputDir(fileName: String) {
        get().asFile.toPath().copyTo(outputDir.file(fileName).get().asFile.toPath(), REPLACE_EXISTING)
    }
}
