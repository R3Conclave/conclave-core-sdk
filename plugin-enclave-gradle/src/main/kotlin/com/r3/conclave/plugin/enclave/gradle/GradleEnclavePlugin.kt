package com.r3.conclave.plugin.enclave.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask.Companion.CONCLAVE_GROUP
import com.r3.conclave.plugin.enclave.gradle.os.LinuxDependentTools
import com.r3.conclave.plugin.enclave.gradle.os.MacOSDependentTools
import com.r3.conclave.plugin.enclave.gradle.os.OSDependentTools
import com.r3.conclave.plugin.enclave.gradle.os.WindowsDependentTools
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.ProjectLayout
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import javax.inject.Inject

@Suppress("UnstableApiUsage")
class GradleEnclavePlugin @Inject constructor(private val layout: ProjectLayout) : Plugin<Project> {
    override fun apply(target: Project) {
        val sdkVersion = readVersionFromPluginManifest()
        target.logger.info("Applying Conclave gradle plugin for version $sdkVersion")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions(target, sdkVersion)

        target.pluginManager.apply(JavaPlugin::class.java)
        target.pluginManager.apply(ShadowPlugin::class.java)

        target.convention.getPlugin(JavaPluginConvention::class.java).apply {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        val conclaveExtension = target.extensions.create("conclave", ConclaveExtension::class.java)

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

        val baseDirectory = target.buildDir.toPath().resolve("conclave")
        val conclaveDependenciesDirectory = "$baseDirectory/com/r3/conclave"

        val osDependentTools = getOSDependentTools(conclaveDependenciesDirectory)

        val copySgxToolsTask = target.createTask<Copy>("copySgxTools") { task ->
            task.group = CONCLAVE_GROUP
            val dependencies= osDependentTools.getToolsDependenciesIDs(sdkVersion)
            task.fromDependencies(*dependencies)
            task.into(baseDirectory)
        }

        val linkerToolFile = target.file(osDependentTools.getLdFile())
        val signToolFile = target.file(osDependentTools.getSgxSign())
        val opensslToolFile = target.file(osDependentTools.getOpensslFile())

        val buildJarObjectTask = target.createTask<BuildJarObject>("buildJarObject") { task ->
            task.dependsOn(copySgxToolsTask)
            task.inputs.files(linkerToolFile, signToolFile)
            task.inputLd.set(linkerToolFile)
            task.inputJar.set(shadowJarTask.archiveFile)
            task.outputJarObject.set(baseDirectory.resolve("app-jar").resolve("app.jar.o").toFile())
        }

        val enclaveClassNameTask = target.createTask<EnclaveClassName>("enclaveClassName") { task ->
            task.inputJar.set(shadowJarTask.archiveFile)
        }

        // Dummy key
        val createDummyKeyTask = target.createTask<GenerateDummyMrsignerKey>("createDummyKey") { task ->
            task.dependsOn(copySgxToolsTask)
            task.inputs.files(opensslToolFile.parent)
            task.opensslTool.set(opensslToolFile)
            task.outputKey.set(baseDirectory.resolve("dummy_key.pem").toFile())
        }

        val linkerScriptFile = baseDirectory.resolve("Enclave.lds")

        val generateReflectionConfigTask = target.createTask<GenerateReflectionConfig>("generateReflectionConfig") { task ->
            task.dependsOn(enclaveClassNameTask)
            task.enclaveClass.set(enclaveClassNameTask.outputEnclaveClassName)
            task.reflectionConfig.set(baseDirectory.resolve("reflectconfig").toFile())
        }

        val copyGraalVM = target.createTask<Copy>("copyGraalVM") { task ->
            task.group = CONCLAVE_GROUP
            task.fromDependencies(
                    "com.r3.conclave:graal:$sdkVersion"
            )
            task.into(baseDirectory)
        }

        val graalVMPath = "$baseDirectory/com/r3/conclave/graalvm"
        val graalVMDistributionPath = "$graalVMPath/distribution"
        val untarGraalVM = target.createTask<Exec>("untarGraalVM") { task ->
            task.group = CONCLAVE_GROUP
            task.dependsOn(copyGraalVM)
            Files.createDirectories(Paths.get(graalVMDistributionPath))
            val graalVMTarPath = "$graalVMPath/graalvm.tar"
            task.inputs.file(graalVMTarPath)
            task.outputs.dir(graalVMDistributionPath)
            task.workingDir(graalVMDistributionPath)
            task.commandLine("tar", "xf", graalVMTarPath)
        }

        for (type in BuildType.values()) {
            val typeLowerCase = type.name.toLowerCase()

            val enclaveExtension = when (type) {
                BuildType.Release -> conclaveExtension.release
                BuildType.Debug -> conclaveExtension.debug
                BuildType.Simulation -> conclaveExtension.simulation
            }

            val enclaveDirectory = baseDirectory.resolve(typeLowerCase)

            // Simulation and debug default to using a dummy key. Release defaults to external key
            val keyType = when (type) {
                BuildType.Release   -> SigningType.ExternalKey
                else                -> SigningType.DummyKey
            }
            enclaveExtension.signingType.set(keyType)

            // Set the default signing material location as an absolute path because if the
            // user overrides it they will use a project relative (rather than build directory
            // relative) path name.
            enclaveExtension.signingMaterial.set(layout.buildDirectory.file("enclave/$type/signing_material.bin"))

            val copyPartialEnclaveTask = target.createTask<Copy>("copyPartialEnclave$type") { task ->
                task.group = CONCLAVE_GROUP
                task.fromDependencies("com.r3.conclave:native-enclave-$typeLowerCase:$sdkVersion")
                task.into(baseDirectory)
            }

            val substrateDependenciesPath = "$conclaveDependenciesDirectory/substratevm/$type"
            val sgxDirectory = "$conclaveDependenciesDirectory/sgx/$type"
            val copySubstrateDependenciesTask = target.createTask<Copy>("copySubstrateDependencies$type") { task ->
                task.group = CONCLAVE_GROUP
                task.fromDependencies(
                        "com.r3.conclave:native-substratevm-$typeLowerCase:$sdkVersion",
                        "com.r3.conclave:linux-sgx-$typeLowerCase:$sdkVersion"
                )
                task.into(baseDirectory)
            }

            val unsignedEnclaveFile = enclaveDirectory.resolve("enclave.so").toFile()

            val buildUnsignedGraalEnclaveTask = target.createTask<NativeImage>("buildUnsignedGraalEnclave$type", type, linkerScriptFile) { task ->
                task.dependsOn(untarGraalVM, copySgxToolsTask, copySubstrateDependenciesTask, generateReflectionConfigTask)
                task.inputs.files(graalVMDistributionPath, sgxDirectory, substrateDependenciesPath, linkerToolFile)
                task.nativeImagePath.set(target.file(graalVMDistributionPath))
                task.jarFile.set(shadowJarTask.archiveFile)
                task.cLibraryPaths.from("$sgxDirectory/tlibc",
                        "$sgxDirectory/libcxx")
                task.libraryPath.set(target.file(sgxDirectory))
                task.libraries.from(
                        "$substrateDependenciesPath/libsubstratevm.a",
                        "$substrateDependenciesPath/libjvm_host_enclave_common_enclave.a",
                        "$substrateDependenciesPath/libjvm_enclave_edl.a",
                        "$substrateDependenciesPath/libz.a"
                )
                task.ldPath.set(linkerToolFile)
                // Libraries in this section are linked with the --whole-archive option which means that
                // nothing is discarded by the linker. This is required if a static library has any constructors
                // or static variables that need to be initialised which would otherwise be discarded by
                // the linker.
                task.librariesWholeArchive.from(
                        "$substrateDependenciesPath/libjvm_enclave_common.a"
                )
                task.reflectionConfiguration.set(generateReflectionConfigTask.reflectionConfig)
                task.maxStackSize.set(conclaveExtension.maxStackSize)
                task.maxHeapSize.set(conclaveExtension.maxHeapSize)
                task.outputEnclave.set(unsignedEnclaveFile)
            }

            val buildUnsignedAvianEnclaveTask = target.createTask<BuildUnsignedAvianEnclave>("buildUnsignedAvianEnclave$type") { task ->
                task.dependsOn(copySgxToolsTask, copyPartialEnclaveTask, buildJarObjectTask)
                val partialEnclavefile = "${copyPartialEnclaveTask.destinationDir}/com/r3/conclave/partial-enclave/$type/jvm_enclave_avian"
                task.inputs.files(linkerToolFile.parent, partialEnclavefile, buildJarObjectTask.outputJarObject)
                task.inputLd.set(linkerToolFile)
                task.inputEnclaveObject.set(target.file(partialEnclavefile))
                task.inputJarObject.set(buildJarObjectTask.outputJarObject)
                task.outputEnclave.set(unsignedEnclaveFile)
                task.stripped.set(type == BuildType.Release)
            }

            val buildUnsignedEnclaveTask = target.createTask<BuildUnsignedEnclave>("buildUnsignedEnclave$type") { task ->
                // This task is used as a common target that selects between Avian and GraalVM based on
                // conclaveExtension.runtime. It sets inputEnclave to the output of the relevant task,
                // selected at build time causing a dependency
                task.inputEnclave.set(conclaveExtension.runtime.flatMap {
                    when (it) {
                        RuntimeType.Avian -> buildUnsignedAvianEnclaveTask.outputEnclave
                        else -> buildUnsignedGraalEnclaveTask.outputEnclave
                    }
                })
                task.outputEnclave.set(task.inputEnclave.get())
            }

            val generateEnclaveConfigTask = target.createTask<GenerateEnclaveConfig>("generateEnclaveConfig$type", type) { task ->
                task.productID.set(conclaveExtension.productID)
                task.revocationLevel.set(conclaveExtension.revocationLevel)
                task.maxHeapSize.set(conclaveExtension.maxHeapSize)
                task.maxStackSize.set(conclaveExtension.maxStackSize)
                task.outputConfigFile.set(enclaveDirectory.resolve("enclave.xml").toFile())
            }

            val signEnclaveWithKeyTask = target.createTask<SignEnclave>("signEnclaveWithKey$type", enclaveExtension, type) { task ->
                task.dependsOn(copySgxToolsTask)
                task.inputs.files(signToolFile.parent, buildUnsignedEnclaveTask.outputEnclave, generateEnclaveConfigTask.outputConfigFile)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclaveTask.outputEnclave)
                task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
                task.inputKey.set(enclaveExtension.signingType.flatMap {
                    when (it) {
                        SigningType.DummyKey -> createDummyKeyTask.outputKey
                        SigningType.PrivateKey -> enclaveExtension.signingKey
                        else -> target.provider { null }
                    }
                })
                task.outputSignedEnclave.set(enclaveDirectory.resolve("enclave.signed.so").toFile())
            }

            val generateEnclaveSigningMaterialTask = target.createTask<GenerateEnclaveSigningMaterial>("generateEnclaveSigningMaterial$type") { task ->
                task.dependsOn(copySgxToolsTask)
                task.inputs.files(signToolFile.parent, buildUnsignedEnclaveTask.outputEnclave, generateEnclaveConfigTask.outputConfigFile, enclaveExtension.signingMaterial)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclaveTask.outputEnclave)
                task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
                task.signatureDate.set(enclaveExtension.signatureDate)
                task.outputSigningMaterial.set(enclaveExtension.signingMaterial)
            }

            val addEnclaveSignatureTask = target.createTask<AddEnclaveSignature>("addEnclaveSignature$type") { task ->
                task.dependsOn(copySgxToolsTask)
                /**
                 * Setting a dependency on a task (at least a `Copy` task) doesn't mean we'll be depending on the task's output.
                 * Despite the dependency task running when out of date, the dependent task would then be considered up-to-date,
                 * even when declaring `dependsOn`.
                 */
                task.inputs.files(signToolFile.parent, generateEnclaveSigningMaterialTask.inputEnclave, generateEnclaveSigningMaterialTask.outputSigningMaterial,
                        generateEnclaveConfigTask.outputConfigFile, enclaveExtension.mrsignerPublicKey, enclaveExtension.mrsignerSignature)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(generateEnclaveSigningMaterialTask.inputEnclave)
                task.inputSigningMaterial.set(generateEnclaveSigningMaterialTask.outputSigningMaterial)
                task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
                task.inputMrsignerPublicKey.set(enclaveExtension.mrsignerPublicKey)
                task.inputMrsignerSignature.set(enclaveExtension.mrsignerSignature)
                task.outputSignedEnclave.set(enclaveDirectory.resolve("enclave.signed.so").toFile())
            }

            val generateEnclaveMetadataTask = target.createTask<GenerateEnclaveMetadata>("generateEnclaveMetadata$type") { task ->
                val signingTask = enclaveExtension.signingType.map {
                    when (it) {
                        SigningType.DummyKey    -> signEnclaveWithKeyTask
                        SigningType.PrivateKey  -> signEnclaveWithKeyTask
                        else                    -> addEnclaveSignatureTask
                    }
                }
                task.dependsOn(signingTask)
                task.inputSignTool.set(signToolFile)
                val signedEnclaveFile = enclaveExtension.signingType.flatMap {
                    when (it) {
                        SigningType.DummyKey -> signEnclaveWithKeyTask.outputSignedEnclave
                        SigningType.PrivateKey -> signEnclaveWithKeyTask.outputSignedEnclave
                        else -> addEnclaveSignatureTask.outputSignedEnclave
                    }
                }
                task.inputSignedEnclave.set(signedEnclaveFile)
                task.inputs.files(signToolFile.parent, signedEnclaveFile)
                task.outputEnclaveMetadata.set(enclaveDirectory.resolve("metadata.yml").toFile())
            }

            val buildSignedEnclaveTask = target.createTask<BuildSignedEnclave>("buildSignedEnclave$type") { task ->
                task.dependsOn(generateEnclaveMetadataTask)
                task.inputs.files(generateEnclaveMetadataTask.inputSignedEnclave)
                task.outputSignedEnclave.set(generateEnclaveMetadataTask.inputSignedEnclave)
            }

            val signedEnclaveJarTask = target.createTask<Jar>("signedEnclave${type}Jar") { task ->
                task.group = CONCLAVE_GROUP
                task.dependsOn(enclaveClassNameTask)
                task.archiveAppendix.set("signed-so")
                task.archiveClassifier.set(typeLowerCase)
                // buildSignedEnclaveTask determines which of the three Conclave supported signing methods
                // to use to sign the enclave and invokes the correct task accordingly.
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

    private fun getOSDependentTools(conclaveDependenciesDirectory: String) : OSDependentTools {
        return when {
            OperatingSystem.current().isMacOsX -> {
                MacOSDependentTools(conclaveDependenciesDirectory)
            }
            OperatingSystem.current().isWindows -> {
                WindowsDependentTools(conclaveDependenciesDirectory)
            }
            else -> {
                LinuxDependentTools(conclaveDependenciesDirectory)
            }
        }
    }

    private fun readVersionFromPluginManifest(): String {
        return GradleEnclavePlugin::class.java.classLoader
                .getResources(MANIFEST_NAME)
                .asSequence()
                .mapNotNull { it.openStream().use(::Manifest).mainAttributes.getValue("Conclave-Version") }
                .firstOrNull() ?: throw IllegalStateException("Could not find Conclave-Version in plugin's manifest")
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

    private inline fun <reified T : Task> Project.createTask(name: String, vararg constructorArgs: Any?, configure: (T) -> Unit): T {
        val task = tasks.create(name, T::class.java, *constructorArgs)
        configure(task)
        return task
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
}
