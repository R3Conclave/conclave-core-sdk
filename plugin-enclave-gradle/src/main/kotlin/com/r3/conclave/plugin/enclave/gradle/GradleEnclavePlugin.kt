package com.r3.conclave.plugin.enclave.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask.Companion.CONCLAVE_GROUP
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.ProjectLayout
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.jvm.tasks.Jar
import java.text.SimpleDateFormat
import java.util.concurrent.Callable
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import javax.inject.Inject

@Suppress("UnstableApiUsage")
class GradleEnclavePlugin @Inject constructor(private val layout: ProjectLayout) : Plugin<Project> {
    override fun apply(target: Project) {
        val sdkVersion = readVersionFromPluginManifest()
        target.logger.info("Applying Conclave gradle plugin for version $sdkVersion")

        val baseDirectory = target.buildDir.toPath().resolve("conclave")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions(target, sdkVersion)

        target.pluginManager.apply(JavaPlugin::class.java)
        target.pluginManager.apply(ShadowPlugin::class.java)

        val shadowJarTask = target.tasks.withType(ShadowJar::class.java).getByName("shadowJar") { task ->
            task.isPreserveFileTimestamps = false
            task.isReproducibleFileOrder = true
            task.fileMode = Integer.parseInt("660", 8)
            task.dirMode = Integer.parseInt("777", 8)
            task.entryCompression = DEFLATED
            task.includeEmptyDirs = false
            task.isCaseSensitive = true
            task.isZip64 = true
            task.archiveClassifier.set("shadow")
        }

        val copySgxToolsTask = target.tasks.create("copySgxTools", Copy::class.java) { task ->
            task.group = CONCLAVE_GROUP
            task.fromDependencies(
                    "com.r3.sgx:native-binutils:$sdkVersion",
                    "com.r3.sgx:native-sign-tool:$sdkVersion"
            )
            task.into(baseDirectory)
        }

        val sgxToolsDirectory = "${copySgxToolsTask.destinationDir}/com/r3/sgx"
        val binutilsDirectory = target.file("$sgxToolsDirectory/binutils")
        val signToolFile = target.file("$sgxToolsDirectory/sign-tool/sgx_sign")

        val buildJarObjectTask = target.tasks.create("buildJarObject", BuildJarObject::class.java) { task ->
            task.dependsOn(copySgxToolsTask)
            task.inputLd.set(target.file("$binutilsDirectory/ld"))
            task.inputJar.set(shadowJarTask.archiveFile)
            task.outputJarObject.set(baseDirectory.resolve("app-jar").resolve("app.jar.o").toFile())
        }

        val enclaveClassNameTask = target.tasks.create("enclaveClassName", EnclaveClassName::class.java) { task ->
            task.inputJar.set(shadowJarTask.archiveFile)
        }

        // Dummy key
        val createDummyKeyTask = target.tasks.create("createDummyKey", GenerateDummyMrsignerKey::class.java) { task ->
            task.outputKey.set(target.file("${target.buildDir}/dummy_key.pem"))
        }

        for (type in BuildType.values()) {
            val copyPartialEnclaveTask = target.tasks.create("copyPartialEnclave$type", Copy::class.java) { task ->
                task.group = CONCLAVE_GROUP
                task.fromDependencies("com.r3.sgx:native-enclave-${type.name.decapitalize()}:$sdkVersion")
                task.into(baseDirectory)
            }

            val buildUnsignedEnclaveTask = target.tasks.create("buildUnsignedEnclave$type", BuildUnsignedEnclave::class.java) { task ->
                task.dependsOn(copySgxToolsTask, copyPartialEnclaveTask)
                task.inputLd.set(target.file("$binutilsDirectory/ld"))
                task.inputEnclaveObject.set(target.file("${copyPartialEnclaveTask.destinationDir}/com/r3/sgx/partial-enclave/$type/jvm_enclave_avian"))
                task.inputJarObject.set(buildJarObjectTask.outputJarObject)
                task.outputEnclave.set(target.file("${target.buildDir}/enclave/$type/enclave.so"))
                task.stripped.set(type == BuildType.Release)
            }

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
                task.dependsOn(copySgxToolsTask)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclaveTask.outputEnclave)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.inputKey.set(createDummyKeyTask.outputKey)
                task.outputSignedEnclave.set(signedEnclaveFile)
            }

            val generateEnclaveSigningMaterialTask = target.tasks.create("generateEnclaveSigningMaterial$type", GenerateEnclaveSigningMaterial::class.java) { task ->
                task.dependsOn(copySgxToolsTask)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclaveTask.outputEnclave)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.signatureDate.set(enclaveExtension.signatureDate)
                task.outputSigningMaterial.set(signingMaterialFile)
            }

            val addEnclaveSignatureTask = target.tasks.create("addEnclaveSignature$type", AddEnclaveSignature::class.java) { task ->
                task.dependsOn(copySgxToolsTask)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(generateEnclaveSigningMaterialTask.inputEnclave)
                task.inputPublicKeyPem.set(enclaveExtension.mrsignerPublicKey)
                task.inputSignature.set(enclaveExtension.mrsignerSignature)
                task.inputSigningMaterial.set(generateEnclaveSigningMaterialTask.outputSigningMaterial)
                task.inputEnclaveConfig.set(enclaveExtension.configuration)
                task.outputSignedEnclave.set(signedEnclaveFile)
            }

            val generateEnclaveMetadataTask = target.tasks.create("generateEnclaveMetadata$type", GenerateEnclaveMetadata::class.java) { task ->
                val signingTask = enclaveExtension.shouldUseDummyKey.map {
                    if (it) signEnclaveWithDummyKeyTask else addEnclaveSignatureTask
                }
                task.dependsOn(signingTask)
                task.inputSignTool.set(signToolFile)
                task.inputSignedEnclave.set(enclaveExtension.shouldUseDummyKey.flatMap {
                    if (it) {
                        signEnclaveWithDummyKeyTask.outputSignedEnclave
                    } else {
                        addEnclaveSignatureTask.outputSignedEnclave
                    }
                })
                task.outputEnclaveMetadata.set(enclaveMetadataFile)
            }

            val buildSignedEnclaveTask = target.tasks.create("buildSignedEnclave$type", BuildSignedEnclave::class.java) { task ->
                task.dependsOn(generateEnclaveMetadataTask)
                task.outputSignedEnclave.set(generateEnclaveMetadataTask.inputSignedEnclave)
            }

            val typeLowerCase = type.name.toLowerCase()

            val signedEnclaveJarTask = target.tasks.create("signedEnclave${type}Jar", Jar::class.java) { task ->
                task.group = CONCLAVE_GROUP
                task.dependsOn(enclaveClassNameTask)
                task.archiveAppendix.set("signed-so")
                task.archiveClassifier.set(typeLowerCase)
                // TODO This task assumes buildSignedEnclave is the sole task that can create the signed so file. This is
                //      currently not the case: https://r3-cev.atlassian.net/browse/CON-26
                task.from(buildSignedEnclaveTask.outputSignedEnclave)
                val packagePath = enclaveClassNameTask.outputEnclaveClassName.map { it.substringBeforeLast('.').replace('.', '/') }
                task.into(packagePath)
                task.rename {
                    "${enclaveClassNameTask.outputEnclaveClassName.get().substringAfterLast('.')}-$typeLowerCase.signed.so"
                }
            }

            // https://docs.gradle.org/current/userguide/cross_project_publications.html
            target.configurations.create(typeLowerCase) {
                it.isCanBeConsumed = true
                it.isCanBeResolved = false
            }

            target.artifacts.add(typeLowerCase, signedEnclaveJarTask)
        }
    }

    private fun readVersionFromPluginManifest(): String {
        return GradleEnclavePlugin::class.java.classLoader
                .getResources(MANIFEST_NAME)
                .asSequence()
                .mapNotNull { it.openStream().use(::Manifest).mainAttributes.getValue("Conclave-Version") }
                .firstOrNull() ?: throw IllegalStateException("Could not find Conclave-Version in plugin's manifest")
    }

    private fun AbstractCopyTask.fromDependencies(vararg dependencyNotations: String) {
        val configuration = project.configurations.create("${name}Configuration")
        dependencyNotations.forEach {
            project.dependencies.add(configuration.name, it)
        }
        fromConfiguration(configuration)
    }

    // https://discuss.gradle.org/t/right-way-to-copy-contents-from-dependency-archives/7449/13
    private fun AbstractCopyTask.fromConfiguration(configuration: Configuration) {
        dependsOn(configuration)
        from(Callable {
            configuration.map(project::zipTree)
        })
    }

    private fun autoconfigureDependencyVersions(target: Project, sdkVersion: String) {
        target.configurations.all { configuration ->
            configuration.withDependencies { dependencySet ->
                dependencySet
                        .filterIsInstance<ExternalDependency>()
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
