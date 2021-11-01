package com.r3.conclave.plugin.enclave.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask.Companion.CONCLAVE_GROUP
import com.r3.conclave.plugin.enclave.gradle.os.LinuxDependentTools
import com.r3.conclave.plugin.enclave.gradle.os.MacOSDependentTools
import com.r3.conclave.plugin.enclave.gradle.os.OSDependentTools
import com.r3.conclave.plugin.enclave.gradle.os.WindowsDependentTools
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.ProjectLayout
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar
import org.gradle.util.VersionNumber
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import javax.inject.Inject

@Suppress("UnstableApiUsage")
class GradleEnclavePlugin @Inject constructor(private val layout: ProjectLayout) : Plugin<Project> {
    override fun apply(target: Project) {
        checkGradleVersionCompatibility(target)

        val sdkVersion = readVersionFromPluginManifest()
        target.logger.info("Applying Conclave gradle plugin for version $sdkVersion")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions(target, sdkVersion)

        target.pluginManager.apply(JavaPlugin::class.java)
        target.pluginManager.apply(ShadowPlugin::class.java)

        setJvmTargetVersion(target)

        val conclaveExtension = target.extensions.create("conclave", ConclaveExtension::class.java)

        target.afterEvaluate {
            // This is called before the build tasks are executed but after the build.gradle file
            // has been parsed. This gives us an opportunity to perform actions based on the user configuration
            // of the enclave.
            val message = "As Avian has been demised, only GraalVM (graalvm_native_image) is supported and the " +
                    "parameter \"runtime\" is now deprecated and can be removed."
            if (conclaveExtension.runtime.isPresent) {
                if (conclaveExtension.runtime.get() == RuntimeType.GraalVMNativeImage) {
                    target.logger.warn(message)
                } else {
                    throw GradleException(message)
                }
            }
            // If language support is enabled then automatically add the required dependency.
            if (conclaveExtension.supportLanguages.get().length > 0) {
                // Please ensure the version number matches the version number set in versions.gradle to avoid
                // incompatibilities
                target.dependencies.add("implementation", "org.graalvm.sdk:graal-sdk:21.2.0")
            }
            // Add dependencies automatically (so developers don't have to)
            target.dependencies.add("implementation", "com.r3.conclave:conclave-enclave:$sdkVersion")
            target.dependencies.add("testImplementation", "com.r3.conclave:conclave-host:$sdkVersion")
            // Make sure that the user has specified productID, print friendly error message if not
            if (!conclaveExtension.productID.isPresent) {
                throw GradleException(
                        "Enclave product ID not specified! " +
                        "Please set the 'productID' property in the build configuration for your enclave.\n" +
                        "If you're unsure what this error message means, please consult the conclave documentation.")
            }
            // Make sure that the user has specified revocationLevel, print friendly error message if not
            if (!conclaveExtension.revocationLevel.isPresent) {
                throw GradleException(
                        "Enclave revocation level not specified! " +
                        "Please set the 'revocationLevel' property in the build configuration for your enclave.\n" +
                        "If you're unsure what this error message means, please consult the conclave documentation.")
            }
        }

        val enclaveClassNameTask = target.createTask<EnclaveClassName>("enclaveClassName") { task ->
            task.dependsOn(target.tasks.withType(JavaCompile::class.java))
            task.inputClassPath.set(getMainSourceSet(target).runtimeClasspath)
        }

        val generateEnclavePropertiesTask =
            target.createTask<GenerateEnclaveProperties>("generateEnclaveProperties") { task ->
                task.dependsOn(enclaveClassNameTask)
                task.resourceDirectory.set(getMainSourceSet(target).output.resourcesDir.toString())
                task.mainClassName.set(enclaveClassNameTask.outputEnclaveClassName)
                task.conclaveExtension.set(conclaveExtension)
            }

        val shadowJarTask = target.tasks.withType(ShadowJar::class.java).getByName("shadowJar") { task ->
            task.dependsOn(generateEnclavePropertiesTask)
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

        // Create configurations for each of the build types.
        for (type in BuildType.values()) {
            // https://docs.gradle.org/current/userguide/cross_project_publications.html
            target.configurations.create(type.name.toLowerCase()) {
                it.isCanBeConsumed = true
                it.isCanBeResolved = false
            }
        }

        // Create the tasks that are required to build the Mock build type artifact.
        createMockArtifact(target, shadowJarTask)

        // Create the tasks that are required build the Release, Debug and Simulation artifacts.
        createEnclaveArtifacts(
                target,
                sdkVersion,
                conclaveExtension,
                shadowJarTask,
                generateEnclavePropertiesTask,
                enclaveClassNameTask)
    }

    /**
     * Get the main source set for a given project
     */
    private fun getMainSourceSet(project: Project): SourceSet {
        return (project.properties["sourceSets"] as SourceSetContainer).getByName("main")
    }

    /**
     * Set Java version for JavaCompile and KotlinCompile tasks.
     * Always set the source/target compatibility to Java 11.
     * If desired this can be overridden at enclave/build.gradle.
     * You might have both Kotlin and Java source code in your enclave project,
     * set JVM compatibility/target for both compileJava and compileKotlin tasks.
     */
    private fun setJvmTargetVersion(project: Project) {
        val javaVersion = "11"

        project.tasks.withType(JavaCompile::class.java) { task ->
            task.sourceCompatibility = javaVersion
            task.targetCompatibility = javaVersion
        }

        try {
            // Using reflection here as Gradle does not know anything about Jetbrains tasks.
            project.tasks.forEach {
                if (it.javaClass.name.startsWith("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")) {
                    setKotlinOptionJvmTarget(it, javaVersion)
                }
            }
        } catch (ex: Exception) {
            throw GradleException("Attempt to automatically set Kotlin JVM target to Java 11 failed. " +
                    "Please manually set your enclave's build target to Java 11.", ex)
        }
    }

    private fun setKotlinOptionJvmTarget(obj: Any, javaVersion: String) {
        /**
         * JvmTarget for KotlinCompile tasks is hidden under KotlinOptions
         *
         * compileKotlin {
         *    kotlinOptions {
         *        jvmTarget = JavaVersion.VERSION_11
         *    }
         * }
         */
        val kotlinOptions = obj.getMethod("getKotlinOptions", false).invoke(obj)
        kotlinOptions.getMethod("setJvmTarget", true, String::class.java).invoke(kotlinOptions, javaVersion)
    }

    /**
     * sometimes you have to query a superclass for declared methods
     */
    private fun Any.getMethod(methodName: String, superclass: Boolean, vararg parameterType: Class<*>): Method {
        return when(superclass) {
            false -> javaClass.getDeclaredMethod(methodName, *parameterType)
                .apply { isAccessible = true }
            true -> javaClass.superclass.getDeclaredMethod(methodName, *parameterType)
                .apply { isAccessible = true }
        }
    }

    private fun createMockArtifact(target: Project, shadowJarTask: ShadowJar) {
        // Mock mode does not require all the enclave building tasks. The enclave Jar file is just packaged
        // as an artifact.
        target.artifacts.add("mock", shadowJarTask.archiveFile)
    }

    private fun createEnclaveArtifacts(
            target: Project,
            sdkVersion: String,
            conclaveExtension: ConclaveExtension,
            shadowJarTask: ShadowJar,
            generateEnclavePropertiesTask: GenerateEnclaveProperties,
            enclaveClassNameTask: EnclaveClassName) {

        val baseDirectory = target.buildDir.toPath().resolve("conclave")
        val conclaveDependenciesDirectory = "$baseDirectory/com/r3/conclave"

        val osDependentTools = getOSDependentTools(conclaveDependenciesDirectory)

        val copyEnclaveCommonHeaders = target.createTask<Copy>("copyEnclaveCommonHeaders") { task ->
            task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            task.group = CONCLAVE_GROUP
            task.fromDependencies("com.r3.conclave:jvm-enclave-common:$sdkVersion")
            task.into(baseDirectory)
        }

        val copySgxToolsTask = target.createTask<Copy>("copySgxTools") { task ->
            task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            task.fromDependencies(*osDependentTools.getToolsDependenciesIDs(sdkVersion).toTypedArray())
            task.into(baseDirectory)
        }

        val nativeImageLinkerToolFile = target.file(osDependentTools.getNativeImageLdFile())
        val signToolFile = target.file(osDependentTools.getSgxSign())

        // Dummy key
        val createDummyKeyTask = target.createTask<GenerateDummyMrsignerKey>("createDummyKey") { task ->
            task.outputKey.set(baseDirectory.resolve("dummy_key.pem").toFile())
        }

        val linkerScriptFile = baseDirectory.resolve("Enclave.lds")

        val generateReflectionConfigTask = target.createTask<GenerateReflectionConfig>("generateReflectionConfig") { task ->
            task.dependsOn(enclaveClassNameTask)
            task.enclaveClass.set(enclaveClassNameTask.outputEnclaveClassName)
            task.reflectionConfig.set(baseDirectory.resolve("reflectconfig").toFile())
        }

        val generateAppResourcesConfigTask =
            target.createTask<GenerateAppResourcesConfig>("generateAppResourcesConfig") { task ->
                task.dependsOn(generateEnclavePropertiesTask)
                task.resourcesDirectory.set(getMainSourceSet(target).output.resourcesDir)
                task.appResourcesConfigFile.set((baseDirectory / "app-resources-config.json").toFile())
            }


        val copyGraalVM = target.createTask<Copy>("copyGraalVM") { task ->
            task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            task.fromDependencies(
                    "com.r3.conclave:graal:$sdkVersion",
                    "com.r3.conclave:conclave-build:$sdkVersion"
            )
            task.into(baseDirectory)
        }

        val linuxExec = target.createTask<LinuxExec>("setupLinuxExecEnvironment") { task ->
            task.dependsOn(copyGraalVM)
            task.inputs.file("$conclaveDependenciesDirectory/docker/Dockerfile")
            task.dockerFile.set(target.file("$conclaveDependenciesDirectory/docker/Dockerfile"))
            task.baseDirectory.set(target.projectDir.toPath().toString())
            task.tag.set("conclave-build:$sdkVersion")
            // Create a 'latest' tag too so users can follow our tutorial documentation using the
            // tag 'conclave-build:latest' rather than looking up the conclave version.
            task.tagLatest.set("conclave-build:latest")
        }

        val graalVMPath = "$baseDirectory/com/r3/conclave/graalvm"
        val graalVMDistributionPath = "$graalVMPath/distribution"
        val capCachePath = "$graalVMPath/cap-cache"
        val untarGraalVM = target.createTask<Exec>("untarGraalVM") { task ->
            task.dependsOn(copyGraalVM)
            Files.createDirectories(Paths.get(graalVMDistributionPath))
            val graalVMTarPath = "$graalVMPath/graalvm.tar"
            task.inputs.file(graalVMTarPath)
            task.outputs.dir(graalVMDistributionPath)
            task.workingDir(graalVMDistributionPath)
            task.commandLine("tar", "xf", graalVMTarPath)
        }

        for (type in BuildType.values().filter { it != BuildType.Mock }) {
            val typeLowerCase = type.name.toLowerCase()

            val enclaveExtension = when (type) {
                BuildType.Release -> conclaveExtension.release
                BuildType.Debug -> conclaveExtension.debug
                BuildType.Simulation -> conclaveExtension.simulation
                else -> throw GradleException("Internal Conclave plugin error. Please contact R3 for help.");
            }

            val enclaveDirectory = baseDirectory.resolve(typeLowerCase)

            // Simulation and debug default to using a dummy key. Release defaults to external key
            val keyType = when (type) {
                BuildType.Release -> SigningType.ExternalKey
                else -> SigningType.DummyKey
            }
            enclaveExtension.signingType.set(keyType)

            // Set the default signing material location as an absolute path because if the
            // user overrides it they will use a project relative (rather than build directory
            // relative) path name.
            enclaveExtension.signingMaterial.set(layout.buildDirectory.file("enclave/$type/signing_material.bin"))

            val substrateDependenciesPath = "$conclaveDependenciesDirectory/substratevm/$type"
            val sgxDirectory = "$conclaveDependenciesDirectory/sgx/$type"
            val copySubstrateDependenciesTask = target.createTask<Copy>("copySubstrateDependencies$type") { task ->
                task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                task.fromDependencies(
                        "com.r3.conclave:native-substratevm-$typeLowerCase:$sdkVersion",
                        "com.r3.conclave:linux-sgx-$typeLowerCase:$sdkVersion"
                )
                task.into(baseDirectory)
            }

            val unsignedEnclaveFile = enclaveDirectory.resolve("enclave.so").toFile()

            val buildUnsignedGraalEnclaveTask = target.createTask<NativeImage>("buildUnsignedGraalEnclave$type", type, linkerScriptFile, linuxExec) { task ->
                task.dependsOn(untarGraalVM, copySgxToolsTask, copySubstrateDependenciesTask, generateReflectionConfigTask, generateAppResourcesConfigTask, linuxExec, copyEnclaveCommonHeaders)
                task.inputs.files(graalVMDistributionPath, sgxDirectory, substrateDependenciesPath, nativeImageLinkerToolFile)
                task.nativeImagePath.set(target.file(graalVMDistributionPath))
                task.capCache.set(target.file(capCachePath))
                task.jarFile.set(shadowJarTask.archiveFile)
                task.includePaths.from(
                        "$conclaveDependenciesDirectory/include"
// There can be conflicts between the host system headers and the ones provided by Intel's SDK.
// The lines bellow should be reintroduced as part of CON-284.
//                        "$sgxDirectory/tlibc",
//                        "$sgxDirectory/libcxx"
                )
                task.libraryPath.set(target.file(sgxDirectory))
                task.libraries.from(
                        "$substrateDependenciesPath/libsubstratevm.a",
                        "$substrateDependenciesPath/libfatfs_enclave.a",
                        "$substrateDependenciesPath/libjvm_host_enclave_common_enclave.a",
                        "$substrateDependenciesPath/libjvm_enclave_edl.a",
                        "$substrateDependenciesPath/libz.a"
                )
                task.ldPath.set(nativeImageLinkerToolFile)
                // Libraries in this section are linked with the --whole-archive option which means that
                // nothing is discarded by the linker. This is required if a static library has any constructors
                // or static variables that need to be initialised which would otherwise be discarded by
                // the linker.
                task.librariesWholeArchive.from(
                        "$substrateDependenciesPath/libjvm_enclave_common.a"
                )
                task.reflectionConfiguration.set(generateReflectionConfigTask.reflectionConfig)
                task.appResourcesConfig.set(generateAppResourcesConfigTask.appResourcesConfigFile)
                task.reflectionConfigurationFiles.from(conclaveExtension.reflectionConfigurationFiles)
                task.serializationConfigurationFiles.from(conclaveExtension.serializationConfigurationFiles)
                task.maxStackSize.set(conclaveExtension.maxStackSize)
                task.maxHeapSize.set(conclaveExtension.maxHeapSize)
                task.supportLanguages.set(conclaveExtension.supportLanguages)
                task.deadlockTimeout.set(conclaveExtension.deadlockTimeout)
                task.outputEnclave.set(unsignedEnclaveFile)
            }

            val buildUnsignedEnclaveTask = target.createTask<BuildUnsignedEnclave>("buildUnsignedEnclave$type") { task ->
                task.inputEnclave.set(buildUnsignedGraalEnclaveTask.outputEnclave)
                task.outputEnclave.set(task.inputEnclave.get())
            }

            val generateEnclaveConfigTask = target.createTask<GenerateEnclaveConfig>("generateEnclaveConfig$type", type) { task ->
                task.productID.set(conclaveExtension.productID)
                task.revocationLevel.set(conclaveExtension.revocationLevel)
                task.maxHeapSize.set(conclaveExtension.maxHeapSize)
                task.maxStackSize.set(conclaveExtension.maxStackSize)
                task.tcsNum.set(conclaveExtension.maxThreads)
                task.outputConfigFile.set(enclaveDirectory.resolve("enclave.xml").toFile())
            }

            val signEnclaveWithKeyTask = target.createTask<SignEnclave>("signEnclaveWithKey$type", enclaveExtension, type, linuxExec) { task ->
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

            val generateEnclaveSigningMaterialTask = target.createTask<GenerateEnclaveSigningMaterial>("generateEnclaveSigningMaterial$type", linuxExec) { task ->
                task.group = CONCLAVE_GROUP
                task.description = "Generate standalone signing material for a ${type} mode enclave that can be used with a HSM."
                task.dependsOn(copySgxToolsTask)
                task.inputs.files(signToolFile.parent, buildUnsignedEnclaveTask.outputEnclave, generateEnclaveConfigTask.outputConfigFile, enclaveExtension.signingMaterial)
                task.signTool.set(signToolFile)
                task.inputEnclave.set(buildUnsignedEnclaveTask.outputEnclave)
                task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
                task.signatureDate.set(enclaveExtension.signatureDate)
                task.outputSigningMaterial.set(enclaveExtension.signingMaterial)
            }

            val addEnclaveSignatureTask = target.createTask<AddEnclaveSignature>("addEnclaveSignature$type", linuxExec) { task ->
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
                task.inputMrsignerPublicKey.set(enclaveExtension.mrsignerPublicKey.map {
                    if (!it.asFile.exists()) {
                        throw GradleException("Your enclave is configured to be signed with an external key but the file specified by 'mrsignerPublicKey' in your "
                                              + "build.gradle does not exist. "
                                              + "Refer to https://docs.conclave.net/signing.html#generating-keys-for-signing-an-enclave for instructions "
                                              + "on how to sign your enclave.")
                    }
                    it
                })
                task.inputMrsignerSignature.set(enclaveExtension.mrsignerSignature.map {
                    if (!it.asFile.exists()) {
                        throw GradleException("Your enclave is configured to be signed with an external key but the file specified by 'mrsignerSignature' in your "
                                              + "build.gradle does not exist. "
                                              + "Refer to https://docs.conclave.net/signing.html#generating-keys-for-signing-an-enclave for instructions "
                                              + "on how to sign your enclave.")
                    }
                    it
                })
                task.outputSignedEnclave.set(enclaveDirectory.resolve("enclave.signed.so").toFile())
            }

            val generateEnclaveMetadataTask = target.createTask<GenerateEnclaveMetadata>("generateEnclaveMetadata$type", type, linuxExec) { task ->
                val signingTask = enclaveExtension.signingType.map {
                    when (it) {
                        SigningType.DummyKey -> signEnclaveWithKeyTask
                        SigningType.PrivateKey -> signEnclaveWithKeyTask
                        else -> addEnclaveSignatureTask
                    }
                }
                task.dependsOn(signingTask)
                task.inputSignTool.set(signToolFile)
                val signedEnclaveFile = enclaveExtension.signingType.flatMap {
                    when (it) {
                        SigningType.DummyKey -> signEnclaveWithKeyTask.outputSignedEnclave
                        SigningType.PrivateKey -> signEnclaveWithKeyTask.outputSignedEnclave
                        else -> {
                            if (!enclaveExtension.mrsignerPublicKey.isPresent) {
                                throw GradleException("Your enclave is configured to be signed with an external key but the configuration 'mrsignerPublicKey' in your "
                                        + "build.gradle does not exist. "
                                        + "Refer to https://docs.conclave.net/signing.html#how-to-configure-signing-for-your-enclaves for instructions "
                                        + "on how to configure signing your enclave.")
                            }
                            if (!enclaveExtension.mrsignerSignature.isPresent) {
                                throw GradleException("Your enclave is configured to be signed with an external key but the configuration 'mrsignerSignature' in your "
                                        + "build.gradle does not exist. "
                                        + "Refer to https://docs.conclave.net/signing.html#how-to-configure-signing-for-your-enclaves for instructions "
                                        + "on how to configure signing your enclave.")
                            }
                            addEnclaveSignatureTask.outputSignedEnclave
                        }
                    }
                }
                task.inputSignedEnclave.set(signedEnclaveFile)
                task.inputs.files(signToolFile.parent, signedEnclaveFile)
            }

            val buildSignedEnclaveTask = target.createTask<BuildSignedEnclave>("buildSignedEnclave$type") { task ->
                task.dependsOn(generateEnclaveMetadataTask)
                task.inputs.files(generateEnclaveMetadataTask.inputSignedEnclave)
                task.outputSignedEnclave.set(generateEnclaveMetadataTask.inputSignedEnclave)
            }

            val signedEnclaveJarTask = target.createTask<Jar>("signedEnclave${type}Jar") { task ->
                task.group = CONCLAVE_GROUP
                task.description = "Compile an ${type}-mode enclave that can be loaded by SGX."
                task.dependsOn(enclaveClassNameTask)
                task.archiveAppendix.set("signed-so")
                task.archiveClassifier.set(typeLowerCase)
                // buildSignedEnclaveTask determines which of the three Conclave supported signing methods
                // to use to sign the enclave and invokes the correct task accordingly.
                task.from(buildSignedEnclaveTask.outputSignedEnclave)
                task.doFirst {
                    val enclaveClassName = enclaveClassNameTask.outputEnclaveClassName.get()
                    task.into(enclaveClassName.substringBeforeLast('.').replace('.', '/'))  // Package location
                    task.rename {
                        "${enclaveClassName.substringAfterLast('.')}-$typeLowerCase.signed.so"
                    }
                }
            }

            target.artifacts.add(typeLowerCase, signedEnclaveJarTask.archiveFile)
        }
    }

    private fun checkGradleVersionCompatibility(target: Project) {
        val gradleVersion = target.gradle.gradleVersion
        if (VersionNumber.parse(gradleVersion).baseVersion < VersionNumber(5, 6, 4, null)) {
            throw GradleException("Project ${target.name} is using Gradle version $gradleVersion but the Conclave " +
                    "plugin requires at least version 5.6.4.")
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
