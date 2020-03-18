package com.r3.sgx.plugin.enclave

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.sgx.plugin.BuildType
import com.r3.sgx.plugin.SGX_GROUP
import net.fornwall.jelf.ElfFile
import net.fornwall.jelf.ElfSegment
import org.apache.commons.io.input.BoundedInputStream
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.ProjectLayout
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.jvm.tasks.Jar
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode.READ_ONLY
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import javax.inject.Inject

typealias SDKVersion = String

@Suppress("UnstableApiUsage")
class SgxEnclavePlugin @Inject constructor(private val layout: ProjectLayout) : Plugin<Project> {
    companion object {
        private const val ENCLAVE_CONFIGURATION_NAME = "sgxEnclave"
        private const val ENCLAVE_CLASS_MANIFEST_ATTRIBUTE = "Enclave-Class"

        /**
         * Returns an input stream over the application jar segment embedded in the enclave .so file.
         */
        private fun getAppJarSegmentInputStream(enclaveFile: Path): InputStream {
            val channel = FileChannel.open(enclaveFile)
            // We use a mapped byte buffer as that's the only entry point where ElfFile doesn't read in the entire file.
            val elfFile = ElfFile.from(channel.map(READ_ONLY, 0, channel.size()))

            // These symbols represent the location of the app jar in the file
            val startSymbol = elfFile.getELFSymbol("_binary_app_jar_start")
            val sizeSymbol = elfFile.getELFSymbol("_binary_app_jar_size")
            if (startSymbol == null || sizeSymbol == null) {
                throw InvalidUserDataException("$enclaveFile is not an .so enclave file")
            }

            val ph = requireNotNull(elfFile.getProgramHeaderByAddress(startSymbol.st_value)) {
                "Unable to locate embedded application jar in $enclaveFile"
            }

            val start = ph.offset + (startSymbol.st_value - ph.virtual_address)
            val size = sizeSymbol.st_value

            // Create an InputStream over the app jar location.
            channel.position(start)
            return BoundedInputStream(Channels.newInputStream(channel), size)
        }

        private fun ElfFile.getProgramHeaderByAddress(virtualAddress: Long): ElfSegment? {
            for (i in 0 until num_ph) {
                val ph = getProgramHeader(i)
                if (virtualAddress >= ph.virtual_address && virtualAddress < (ph.virtual_address + ph.mem_size)) {
                    return ph
                }
            }
            return null
        }

        fun readEnclaveClassNameFromEnclaveFile(enclaveFile: Path): String {
            return JarInputStream(getAppJarSegmentInputStream(enclaveFile)).use {
                it.manifest.mainAttributes.getValue(ENCLAVE_CLASS_MANIFEST_ATTRIBUTE)
            } ?: throw InvalidUserDataException("Attribute '$ENCLAVE_CLASS_MANIFEST_ATTRIBUTE' missing from $enclaveFile")
        }

        fun readEnclaveClassNameFromJar(jarFile: Path): String {
            return JarInputStream(Files.newInputStream(jarFile)).use {
                it.manifest.mainAttributes.getValue(ENCLAVE_CLASS_MANIFEST_ATTRIBUTE)
            } ?: throw InvalidUserDataException("Attribute '$ENCLAVE_CLASS_MANIFEST_ATTRIBUTE' missing from $jarFile")
        }

        private fun readVersionFromPluginManifest(): SDKVersion {
            val classLoader = SgxEnclavePlugin::class.java.classLoader
            val manifestUrls = classLoader.getResources(MANIFEST_NAME).toList()
            for (manifestUrl in manifestUrls) {
                return manifestUrl.openStream().use {
                    Manifest(it).mainAttributes.getValue("Conclave-Version")
                } ?: continue
            }
            throw IllegalStateException("Could not find Conclave-Version in plugin's manifest")
        }
    }

    override fun apply(target: Project) {
        val sdkVersion = readVersionFromPluginManifest()
        val baseDirectory = target.buildDir.toPath().resolve("sgx-plugin")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions(target, sdkVersion)

        target.logger.info("[Conclave] Applying the shadow plugin")
        if (!target.pluginManager.hasPlugin("java")) {
            // The Shadow plugin doesn't do anything unless either the java or application
            // plugins are present, so we must ensure it's applied.
            target.pluginManager.apply(JavaPlugin::class.java)
        }
        target.pluginManager.apply(ShadowPlugin::class.java)

        target.logger.info("[Conclave] Configuring shadowJar task")
        val shadowJar = target.tasks.getByName("shadowJar") { task ->
            task as ShadowJar
            task.isPreserveFileTimestamps = false
            task.isReproducibleFileOrder = true
            task.fileMode = Integer.parseInt("660", 8)
            task.dirMode = Integer.parseInt("777", 8)
            task.entryCompression = DEFLATED
            task.includeEmptyDirs = false
            task.isCaseSensitive = true
            task.isZip64 = true
        }
        target.configurations.create(ENCLAVE_CONFIGURATION_NAME)
        target.artifacts.add(ENCLAVE_CONFIGURATION_NAME, shadowJar)

        target.logger.info("[Conclave] Adding copyBinutils task")
        val binutilsConfigurationName = "sgxBinutils"
        val binutilsConfiguration: Configuration = target.configurations.create(binutilsConfigurationName)
        target.dependencies.add(binutilsConfigurationName, "com.r3.sgx:native-binutils:$sdkVersion")
        val copyBinutils = target.tasks.create("copyBinutils", Copy::class.java) { task ->
            task.group = SGX_GROUP
            task.dependsOn(binutilsConfiguration)
            task.from(binutilsConfiguration.map(target::zipTree))
            task.into(target.file("$baseDirectory/binutils"))
        }
        val binutilsDirectory = target.file("${copyBinutils.destinationDir}/com/r3/sgx/binutils")

        target.logger.info("[Conclave] Adding shadowJarObject task")
        val shadowJarObject = target.tasks.create("shadowJarObject", BuildJarObject::class.java) { task ->
            task.dependsOn(shadowJar, copyBinutils)
            task.inputBinutilsDirectory.set(binutilsDirectory)
            task.inputJar.set(layout.file(target.provider { shadowJar.outputs.files.single() }))
            task.outputDir.set(target.file("$baseDirectory/app-jar"))
        }

        target.logger.info("[Conclave] Adding partial enclave dependencies")
        for (type in BuildType.values()) {
            val configurationName = "partialEnclave${type.name}"
            target.configurations.create(configurationName)
            target.dependencies.add(configurationName, "com.r3.sgx:native-enclave-${type.name.decapitalize()}:$sdkVersion")
        }

        target.logger.info("[Conclave] Adding copyPartialEnclave* tasks")
        for (type in BuildType.values()) {
            target.tasks.create("copyPartialEnclave${type.name}", Copy::class.java) { task ->
                task.group = SGX_GROUP
                val enclaveConfiguration = target.configurations.getByName("partialEnclave${type.name}")
                task.dependsOn(enclaveConfiguration)
                task.from(enclaveConfiguration.map(target::zipTree))
                task.into(target.file("$baseDirectory/partial-enclaves"))
            }
        }

        target.logger.info("[Conclave] Adding buildUnsignedEnclave* tasks")
        for (type in BuildType.values()) {
            with(target.tasks.create("buildUnsignedEnclave$type", BuildUnsignedEnclave::class.java)) {
                val copyTask = target.tasks.getByName("copyPartialEnclave${type.name}") as Copy
                dependsOn(copyBinutils, copyTask, shadowJarObject)
                inputBinutilsDirectory.set(binutilsDirectory)
                inputEnclaveObject.set(target.file("${copyTask.destinationDir}/com/r3/sgx/partial-enclave/$type/jvm_enclave_avian"))
                inputJarObject.set(shadowJarObject.outputJarObject)
                outputEnclave.set(target.file("${target.buildDir}/enclave/$type/enclave.so"))
                stripped.set(type == BuildType.Release)
            }
        }

        // Testing
        target.logger.info("[Conclave] Adding copySignTool task")
        val signToolConfiguration = target.configurations.create("signTool")
        target.dependencies.add("signTool", "com.r3.sgx:native-sign-tool:$sdkVersion")
        val copySignTool = target.tasks.create("copySignTool", Copy::class.java) { task ->
            task.group = SGX_GROUP
            task.dependsOn(signToolConfiguration)
            task.from(signToolConfiguration.map(target::zipTree))
            task.into(target.file("$baseDirectory/sign-tool"))
        }

        val signToolFile = target.file("${copySignTool.destinationDir}/com/r3/sgx/sign-tool/sgx_sign")

        // Dummy key
        target.logger.info("[Conclave] Adding createDummyKey task")
        val createDummyKey = target.tasks.create("createDummyKey", GenerateDummyMrsignerKey::class.java) { task ->
            task.outputKey.set(target.file("${target.buildDir}/dummy_key.pem"))
        }

        // Enclave configuration, signing
        for (type in BuildType.values()) {
            val defaultEnclaveConfiguration = layout.projectDirectory.file("src/sgx/$type/enclave.xml")
            val signedEnclaveFile = layout.buildDirectory.file("enclave/$type/enclave.signed.so")
            val enclaveMetadataFile = layout.buildDirectory.file("enclave/$type/enclave.metadata.yml")
            val signingMaterialFile = layout.buildDirectory.file("enclave/$type/signing_material.bin")
            val defaultShouldUseDummyKey = true
            val defaultMrsignerPublicKey = layout.projectDirectory.file("src/sgx/$type/mrsigner.public.pem")
            val defaultMrsignerSignature = layout.projectDirectory.file("src/sgx/$type/mrsigner.signature.bin")
            val defaultSignatureDate = SimpleDateFormat("yyyymmdd").parse("19700101")

            val enclaveExtension = target.extensions.create("enclave$type",
                    EnclaveExtension::class.java,
                    defaultEnclaveConfiguration,
                    defaultShouldUseDummyKey,
                    defaultMrsignerPublicKey,
                    defaultMrsignerSignature,
                    defaultSignatureDate
            )

            val signEnclaveWithDummyKeyTask = target.tasks.create("signEnclaveWithDummyKey$type", SignEnclave::class.java) { task ->
                val buildUnsignedEnclave = target.tasks.getByName("buildUnsignedEnclave$type") as BuildUnsignedEnclave
                task.dependsOn(copySignTool, buildUnsignedEnclave, createDummyKey)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclave.outputEnclave)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.inputKey.set(createDummyKey.outputKey)
                task.outputSignedEnclave.set(signedEnclaveFile)
            }

            val getEnclaveSigningMaterialTask = target.tasks.create("getEnclaveSigningMaterial$type", GetEnclaveSigningMaterial::class.java) { task ->
                val buildUnsignedEnclave = target.tasks.getByName("buildUnsignedEnclave$type") as BuildUnsignedEnclave
                task.dependsOn(copySignTool, buildUnsignedEnclave)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclave.outputEnclave)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.outputSigningMaterial.set(signingMaterialFile)
                task.signatureDate.set(enclaveExtension.signatureDate)
            }

            val addEnclaveSignatureTask = target.tasks.create("addEnclaveSignature$type", AddEnclaveSignature::class.java) { task ->
                task.dependsOn(getEnclaveSigningMaterialTask)
                task.inputEnclave.set(getEnclaveSigningMaterialTask.inputEnclave)
                task.inputPublicKeyPem.set(enclaveExtension.mrsignerPublicKey)
                task.inputSignature.set(enclaveExtension.mrsignerSignature)
                task.inputSigningMaterial.set(signingMaterialFile)
                task.signTool.set(signToolFile)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.outputSignedEnclave.set(signedEnclaveFile)
            }

            val generateEnclaveletMetadataTask = target.tasks.create("generateEnclaveletMetadata$type", GenerateEnclaveletMetadata::class.java) { task ->
                val signingTask = enclaveExtension.shouldUseDummyKey.map {
                    if (it) signEnclaveWithDummyKeyTask else addEnclaveSignatureTask
                }
                task.dependsOn(signingTask)
                val signedEnclave = enclaveExtension.shouldUseDummyKey.flatMap {
                    if (it) {
                        signEnclaveWithDummyKeyTask.outputSignedEnclave
                    } else {
                        addEnclaveSignatureTask.outputSignedEnclave
                    }
                }
                task.inputSignedEnclave.set(signedEnclave)
                task.outputEnclaveMetadata.set(enclaveMetadataFile)
                task.signTool.set(signToolFile)
                task.outputs.file(enclaveMetadataFile)
            }

            val buildSignedEnclaveTask = target.tasks.create("buildSignedEnclave$type", BuildSignedEnclave::class.java) { task ->
                task.dependsOn(generateEnclaveletMetadataTask)
                task.outputSignedEnclave.set(generateEnclaveletMetadataTask.inputSignedEnclave)
                task.outputs.file(signedEnclaveFile)
            }

            val typeLowerCase = type.name.toLowerCase()

            val signedEnclaveJarTask = target.tasks.create("signedEnclave${type}Jar", Jar::class.java) { task ->
                task.archiveAppendix.set("signed-so")
                task.archiveClassifier.set(typeLowerCase)
                // TODO This task assumes buildSignedEnclave is the sole task that can create the signed so file. This is
                //      currently not the case: https://r3-cev.atlassian.net/browse/CON-26
                task.from(buildSignedEnclaveTask.outputSignedEnclave)  // Automatically depend on buildSignedEnclave
                task.doFirst {
                    val enclaveClassName = readEnclaveClassNameFromEnclaveFile(signedEnclaveFile.get().asFile.toPath())
                    val packagePath = enclaveClassName.substringBeforeLast('.').replace('.', '/')
                    task.into(packagePath)
                    task.rename { "${enclaveClassName.substringAfterLast('.')}-$typeLowerCase.signed.so" }
                }
            }

            // https://docs.gradle.org/current/userguide/cross_project_publications.html
            target.configurations.create(typeLowerCase) {
                it.isCanBeConsumed = true
                it.isCanBeResolved = false
            }

            target.artifacts.add(typeLowerCase, signedEnclaveJarTask)
        }

        target.tasks.create("signedEnclaveJars") { task ->
            task.setDependsOn(BuildType.values().map { "signedEnclave${it}Jar" })
        }
    }

    private fun autoconfigureDependencyVersions(target: Project, sdkVersion: SDKVersion) {
        target.configurations.all { configuration ->
            configuration.withDependencies { dependencySet ->
                dependencySet.filterIsInstance<ExternalDependency>()
                        .filter { it.group == "com.r3.conclave" && it.version.isNullOrEmpty() }
                        .forEach { dep ->
                            dep.version {
                                it.require(sdkVersion)
                            }
                        }
            }
        }
    }
}
