package com.r3.sgx.plugin.enclave

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.sgx.plugin.BuildType
import com.r3.sgx.plugin.SGX_GROUP
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.ZipEntryCompression.*
import java.io.InputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import javax.inject.Inject

data class ArtifactMetadata(
        val version: String,
        val mavenRepository: String
)

class SgxEnclavePlugin @Inject constructor(private val layout: ProjectLayout) : Plugin<Project> {
    companion object {
        private const val enclaveConfigurationName = "sgxEnclave"

        private fun readObliviumArtifactMetadataFromManifest(): ArtifactMetadata {
            val classLoader = SgxEnclavePlugin::class.java.classLoader
            val manifestUrls = classLoader.getResources(MANIFEST_NAME).toList()
            for (manifestUrl in manifestUrls) {
                return manifestUrl.openStream().use(::getArtifactMetadataFromManifestStream) ?: continue
            }
            throw IllegalStateException("Could not find Oblivium-Version in plugin's manifest")
        }

        private fun getArtifactMetadataFromManifestStream(manifestStream: InputStream): ArtifactMetadata? {
            val manifest = Manifest(manifestStream)
            val version = manifest.mainAttributes.getValue("Oblivium-Version") ?: return null
            val repository = manifest.mainAttributes.getValue("Oblivium-Maven-Repository") ?: return null
            return ArtifactMetadata(
                    version = version,
                    mavenRepository = repository
            )
        }
    }

    override fun apply(target: Project) {
        val metadata = readObliviumArtifactMetadataFromManifest()
        val baseDirectory = target.buildDir.toPath().resolve("sgx-plugin")
        // Enclave
        target.logger.info("Applying the shadow plugin")
        target.pluginManager.apply(ShadowPlugin::class.java)

        target.logger.info("Adding maven repository")
        target.repositories.maven {
            it.url = URI.create(metadata.mavenRepository)
        }

        target.logger.info("Configuring shadowJar task")
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
        target.configurations.create(enclaveConfigurationName)
        target.artifacts.add(enclaveConfigurationName, shadowJar)

        target.logger.info("Adding copyBinutils task")
        val binutilsConfigurationName = "sgxBinutils"
        val binutilsConfiguration = target.configurations.create(binutilsConfigurationName)
        target.dependencies.add(binutilsConfigurationName, "com.r3.sgx:native-binutils:${metadata.version}")
        val copyBinutils = target.tasks.create("copyBinutils", Copy::class.java) { task ->
            task.group = SGX_GROUP
            task.dependsOn(binutilsConfiguration)
            task.from(binutilsConfiguration.map(target::zipTree))
            task.into(target.file("$baseDirectory/binutils"))
        }
        val binutilsDirectory = target.file("${copyBinutils.destinationDir}/com/r3/sgx/binutils")

        target.logger.info("Adding shadowJarObject task")
        val shadowJarObject = target.tasks.create("shadowJarObject", BuildJarObject::class.java) { task ->
            task.dependsOn(shadowJar, copyBinutils)
            task.inputBinutilsDirectory.set(binutilsDirectory)
            task.inputJar.set(layout.file(target.provider { shadowJar.outputs.files.single() }))
            task.outputDir.set(target.file("$baseDirectory/app-jar"))
        }

        target.logger.info("Adding partial enclave dependencies")
        for (type in BuildType.values()) {
            val configurationName = "partialEnclave${type.name}"
            target.configurations.create(configurationName)
            target.dependencies.add(configurationName, "com.r3.sgx:native-enclave-${type.name.decapitalize()}:${metadata.version}")
        }

        target.logger.info("Adding copyPartialEnclave* tasks")
        for (type in BuildType.values()) {
            target.tasks.create("copyPartialEnclave${type.name}", Copy::class.java) { task ->
                task.group = SGX_GROUP
                val enclaveConfiguration = target.configurations.getByName("partialEnclave${type.name}")
                task.dependsOn(enclaveConfiguration)
                task.from(enclaveConfiguration.map(target::zipTree))
                task.into(target.file("$baseDirectory/partial-enclaves"))
            }
        }

        target.logger.info("Adding buildUnsignedEnclave* tasks")
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
        target.logger.info("Adding copySignTool task")
        val signToolConfiguration = target.configurations.create("signTool")
        target.dependencies.add("signTool", "com.r3.sgx:native-sign-tool:${metadata.version}")
        val copySignTool = target.tasks.create("copySignTool", Copy::class.java) { task ->
            task.group = SGX_GROUP
            task.dependsOn(signToolConfiguration)
            task.from(signToolConfiguration.map(target::zipTree))
            task.into(target.file("$baseDirectory/sign-tool"))
        }

        val signToolFile = target.file("${copySignTool.destinationDir}/com/r3/sgx/sign-tool/sgx_sign")

        // Dummy key
        target.logger.info("Adding createDummyKey task")
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

            val signEnclaveWithDummyKey = target.tasks.create("signEnclaveWithDummyKey$type", SignEnclave::class.java) { task ->
                val buildUnsignedEnclave = target.tasks.getByName("buildUnsignedEnclave$type") as BuildUnsignedEnclave
                task.dependsOn(copySignTool, buildUnsignedEnclave, createDummyKey)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclave.outputEnclave)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.inputKey.set(createDummyKey.outputKey)
                task.outputSignedEnclave.set(signedEnclaveFile)
            }

            val getEnclaveSigningMaterial = target.tasks.create("getEnclaveSigningMaterial$type", GetEnclaveSigningMaterial::class.java) { task ->
                val buildUnsignedEnclave = target.tasks.getByName("buildUnsignedEnclave$type") as BuildUnsignedEnclave
                task.dependsOn(copySignTool, buildUnsignedEnclave)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclave.outputEnclave)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.outputSigningMaterial.set(signingMaterialFile)
                task.signatureDate.set(enclaveExtension.signatureDate)
            }

            val addEnclaveSignature = target.tasks.create("addEnclaveSignature$type", AddEnclaveSignature::class.java) { task ->
                task.dependsOn(getEnclaveSigningMaterial)
                task.inputEnclave.set(getEnclaveSigningMaterial.inputEnclave)
                task.inputPublicKeyPem.set(enclaveExtension.mrsignerPublicKey)
                task.inputSignature.set(enclaveExtension.mrsignerSignature)
                task.inputSigningMaterial.set(signingMaterialFile)
                task.signTool.set(signToolFile)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.outputSignedEnclave.set(signedEnclaveFile)
            }

            val signingTask = enclaveExtension.shouldUseDummyKey.map {
                if (it) {
                    signEnclaveWithDummyKey
                } else {
                    addEnclaveSignature
                }
            }

            val generateEnclaveletMetadata = target.tasks.create("generateEnclaveletMetadata$type", GenerateEnclaveletMetadata::class.java) { task ->
                val signedEnclave = enclaveExtension.shouldUseDummyKey.flatMap {
                    if (it) {
                        signEnclaveWithDummyKey.outputSignedEnclave
                    } else {
                        addEnclaveSignature.outputSignedEnclave
                    }
                }
                task.outputSignedEnclave.set(signedEnclave)
                task.outputEnclaveMetadata.set(enclaveMetadataFile)
                task.signTool.set(signToolFile)
                task.enclaveletJar.set(shadowJarObject.inputJar)
                task.dependsOn(signingTask)
                task.outputs.file(enclaveMetadataFile)
            }

            target.tasks.create("buildSignedEnclave$type", BuildSignedEnclave::class.java) { task ->
                task.outputSignedEnclave.set(generateEnclaveletMetadata.outputSignedEnclave)
                task.dependsOn(generateEnclaveletMetadata)
                task.outputs.file(signedEnclaveFile)
            }
        }
    }
}
