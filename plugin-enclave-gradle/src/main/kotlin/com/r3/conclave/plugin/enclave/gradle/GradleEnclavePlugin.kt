package com.r3.conclave.plugin.enclave.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask.Companion.CONCLAVE_GROUP
import com.r3.conclave.utilities.internal.copyResource
import com.r3.conclave.plugin.enclave.gradle.gramine.BuildUnsignedGramineEnclave
import org.gradle.api.*
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.ProjectLayout
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.ZipEntryCompression.DEFLATED
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.util.VersionNumber
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.setPosixFilePermissions

class GradleEnclavePlugin @Inject constructor(private val layout: ProjectLayout) : Plugin<Project> {
    companion object {
        const val CONCLAVE_GRAALVM_VERSION = "22.0.0.2-1.3"
    }

    override fun apply(target: Project) {
        checkGradleVersionCompatibility(target)

        val sdkVersion = readVersionFromPluginManifest()
        target.logger.info("Applying Conclave gradle plugin for version $sdkVersion")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions(target, sdkVersion)

        target.pluginManager.apply(JavaPlugin::class.java)
        target.pluginManager.apply(ShadowPlugin::class.java)

        val conclaveExtension = target.extensions.create("conclave", ConclaveExtension::class.java)

        target.afterEvaluate {
            // This is called before the build tasks are executed but after the build.gradle file
            // has been parsed. This gives us an opportunity to perform actions based on the user configuration
            // of the enclave.
            // If language support is enabled then automatically add the required dependency.
            if (conclaveExtension.supportLanguages.get().isNotEmpty()) {
                // It might be possible that the conclave part of the version not match the current version, e.g. if
                // SDK is 1.4-SNAPSHOT but we're still using 20.0.0.2-1.3 because we've not had the need to update
                target.dependencies.add("implementation", "com.r3.conclave:graal-sdk:$CONCLAVE_GRAALVM_VERSION")
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
            // Check the passed runtime type is valid.
            try {
                RuntimeType.fromString(conclaveExtension.runtime.get())
            } catch (e: IllegalArgumentException) {
                throw GradleException(
                        "'${conclaveExtension.runtime.get()}' is not a valid enclave runtime type.\n" +
                        "Valid runtime types are: ${RuntimeType.values().map { it.name }}.")
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
            target.configurations.create(type.name.lowercase()) {
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
                enclaveClassNameTask
        )
    }

    private fun buildUnsignedGramineEnclaveTask(target: Project, type: BuildType, ext: ConclaveExtension): BuildUnsignedGramineEnclave {
        val gramineBuildDir = baseDirectory.resolve("gramine").toString()
        return target.createTask("buildUnsignedGramineEnclave$type") { task ->
            task.outputs.dir(gramineBuildDir)
            task.buildDirectory.set(gramineBuildDir)
            task.archLibDirectory.set("/lib/x86_64-linux-gnu")
            // TODO: Once we have integrated Gramine properly, we should use the java executable path
            task.entryPoint.set("/usr/lib/jvm/java-17-openjdk-amd64/bin/java")
            task.maxThreads.set(ext.maxThreads.get())
            task.outputManifest.set(
                Paths.get(gramineBuildDir).resolve(BuildUnsignedGramineEnclave.MANIFEST_DIRECT).toFile()
            )
            task.outputGramineEnclaveMetadata.set(
                Paths.get(gramineBuildDir).resolve(BuildUnsignedGramineEnclave.METADATA_DIRECT).toFile()
            )
        }
    }

    private fun signedEnclaveGramine(
        target: Project,
        enclaveClassNameTask: EnclaveClassName,
        shadowJarTask: ShadowJar,
        type: BuildType,
        buildUnsignedGramineEnclaveTask: BuildUnsignedGramineEnclave
    ): TaskProvider<Jar> {
        val typeLowerCase = type.name.lowercase()

        val task =
            target.tasks.register("signedEnclave${type}${RuntimeType.Gramine.name}Jar", Jar::class.java) { task ->
                task.group = CONCLAVE_GROUP
                task.description = "Compile a ${type}-mode enclave that can be loaded by SGX."
                task.archiveFileName.set("enclave-gramine-$typeLowerCase.jar")
                task.archiveAppendix.set("jar")
                task.archiveClassifier.set(typeLowerCase)
                task.from(shadowJarTask.archiveFile, buildUnsignedGramineEnclaveTask.outputManifest, buildUnsignedGramineEnclaveTask.outputGramineEnclaveMetadata)
                task.doFirst(IntoGramineTask(enclaveClassNameTask, typeLowerCase))
            }
        return task
    }

    inner class IntoGramineTask(
        private val enclaveClassNameTask: EnclaveClassName,
        private val typeLowerCase: String
    ) : Action<Task> {
        override fun execute(task: Task) {
            //  Note that in Gramine we use the class name as a folder,
            //     not as a part of a file, as it is done with Native tasks
            val location = enclaveClassNameTask.outputEnclaveClassName.get().replace('.', '/') + "-${typeLowerCase}"
            println("location $location")
            val jarTask = task as Jar
            jarTask.into(location)
        }
    }

    fun signToolPath(): Path = getSgxTool("sgx_sign")

    fun ldPath(): Path = getSgxTool("ld")

    private fun getSgxTool(name: String): Path {
        val path = baseDirectory / "sgx-tools" / name
        if (!path.exists()) {
            path.parent.createDirectories()
            javaClass.copyResource("/sgx-tools/$name", path)
            path.setPosixFilePermissions(path.getPosixFilePermissions() + OWNER_EXECUTE)
        }
        return path
    }

    private val baseDirectory: Path by lazy { layout.buildDirectory.get().asFile.toPath() / "conclave" }

    /**
     * Get the main source set for a given project
     */
    private fun getMainSourceSet(project: Project): SourceSet {
        return (project.properties["sourceSets"] as SourceSetContainer).getByName("main")
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
        enclaveClassNameTask: EnclaveClassName
    ) {
        // Dummy key
        val createDummyKeyTask = target.createTask<GenerateDummyMrsignerKey>("createDummyKey") { task ->
            task.outputKey.set(baseDirectory.resolve("dummy_key.pem").toFile())
        }

        val linkerScriptFile = baseDirectory.resolve("Enclave.lds")

        val generateReflectionConfigTask =
            target.createTask<GenerateReflectionConfig>("generateReflectionConfig") { task ->
                task.dependsOn(enclaveClassNameTask)
                task.enclaveClass.set(enclaveClassNameTask.outputEnclaveClassName)
                task.reflectionConfig.set(baseDirectory.resolve("reflectconfig").toFile())
            }

        val generateAppResourcesConfigTask =
            target.createTask<GenerateAppResourcesConfig>("generateAppResourcesConfig") { task ->
                task.dependsOn(shadowJarTask)
                task.jarFile.set(shadowJarTask.archiveFile)
                task.appResourcesConfigFile.set((baseDirectory / "app-resources-config.json").toFile())
            }

        val graalVMDistributionPath = "$baseDirectory/graalvm"

        val copyGraalVM = target.createTask<Exec>("copyGraalVM") { task ->

            task.outputs.dir(graalVMDistributionPath)

            // Create a configuration for downloading graalvm-*.tar.gz using Gradle
            val graalVMConfigName = "${task.name}Config"
            val configuration = target.configurations.create(graalVMConfigName)
            target.dependencies.add(graalVMConfigName, "com.r3.conclave:graalvm:$CONCLAVE_GRAALVM_VERSION@tar.gz")
            task.dependsOn(configuration)

            // This is a hack to delay the execution of the code inside toString.
            // Gradle has three stages, initialization, configuration, and execution.
            // The code inside the toString function must run during the execution stage. For that to happen,
            // the following wrapper was created
            class LazyGraalVmFile(target: Project) {
                val graalVMAbsolutePath by lazy { target.configurations.findByName(graalVMConfigName)!!.files.single() { it.name.endsWith("tar.gz") }.absolutePath }
                override fun toString(): String {
                    return graalVMAbsolutePath
                }
            }

            // Uncompress the graalvm-*.tar.gz
            Files.createDirectories(Paths.get(graalVMDistributionPath))
            task.workingDir(graalVMDistributionPath)
            task.commandLine("tar", "xf", LazyGraalVmFile(target))
        }

        val linuxExec = target.createTask<LinuxExec>("setupLinuxExecEnvironment") { task ->
            task.dependsOn(copyGraalVM)
            task.baseDirectory.set(target.projectDir.toPath().toString())
            task.tag.set("conclave-build:$sdkVersion")
            // Create a 'latest' tag too so users can follow our tutorial documentation using the
            // tag 'conclave-build:latest' rather than looking up the conclave version.
            task.tagLatest.set("conclave-build:latest")
        }

        for (type in BuildType.values().filter { it != BuildType.Mock }) {
            val typeLowerCase = type.name.lowercase()

            val enclaveExtension = when (type) {
                BuildType.Release -> conclaveExtension.release
                BuildType.Debug -> conclaveExtension.debug
                BuildType.Simulation -> conclaveExtension.simulation
                else -> throw IllegalStateException()
            }

            val enclaveDirectory = baseDirectory.resolve(typeLowerCase)

            // Simulation and debug default to using a dummy key. Release defaults to external key
            val keyType = when (type) {
                BuildType.Release -> SigningType.ExternalKey
                else -> SigningType.DummyKey
            }
            enclaveExtension.signingType.set(keyType)

            // Gramine related tasks
            val buildUnsignedGramineEnclaveTask = buildUnsignedGramineEnclaveTask(target, type, conclaveExtension)

            val signedEnclaveGramineJarTask = signedEnclaveGramine(
                target,
                enclaveClassNameTask,
                shadowJarTask,
                type,
                buildUnsignedGramineEnclaveTask
            )

            // GraalVM related tasks
            // Set the default signing material location as an absolute path because if the
            // user overrides it they will use a project relative (rather than build directory
            // relative) path name.
            enclaveExtension.signingMaterial.set(layout.buildDirectory.file("enclave/$type/signing_material.bin"))

            val unsignedEnclaveFile = enclaveDirectory.resolve("enclave.so").toFile()

            val buildUnsignedGraalEnclaveTask = target.createTask<NativeImage>(
                "buildUnsignedGraalEnclave$type",
                this,
                type,
                linkerScriptFile,
                linuxExec
            ) { task ->
                task.dependsOn(
                    copyGraalVM,
                    generateReflectionConfigTask,
                    generateAppResourcesConfigTask,
                    linuxExec
                )
                task.inputs.files(graalVMDistributionPath)
                task.nativeImagePath.set(target.file(graalVMDistributionPath))
                task.jarFile.set(shadowJarTask.archiveFile)
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

            val buildUnsignedEnclaveTask =
                target.createTask<BuildUnsignedEnclave>("buildUnsignedEnclave$type") { task ->
                    task.inputEnclave.set(buildUnsignedGraalEnclaveTask.outputEnclave)
                    task.outputEnclave.set(task.inputEnclave.get())
                }

            val generateEnclaveConfigTask =
                target.createTask<GenerateEnclaveConfig>("generateEnclaveConfig$type", type) { task ->
                    task.productID.set(conclaveExtension.productID)
                    task.revocationLevel.set(conclaveExtension.revocationLevel)
                    task.maxHeapSize.set(conclaveExtension.maxHeapSize)
                    task.maxStackSize.set(conclaveExtension.maxStackSize)
                    task.tcsNum.set(conclaveExtension.maxThreads)
                    task.outputConfigFile.set(enclaveDirectory.resolve("enclave.xml").toFile())
                }

            val signEnclaveWithKeyTask = target.createTask<SignEnclave>(
                    "signEnclaveWithKey$type",
                    this,
                    enclaveExtension,
                    type,
                    linuxExec
                ) { task ->
                    task.inputs.files(
                        buildUnsignedEnclaveTask.outputEnclave,
                        generateEnclaveConfigTask.outputConfigFile
                    )
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

            val generateEnclaveSigningMaterialTask = target.createTask<GenerateEnclaveSigningMaterial>(
                "generateEnclaveSigningMaterial$type",
                this,
                linuxExec
            ) { task ->
                task.description = "Generate standalone signing material for a $type mode enclave that can be used " +
                        "with an external signing source."
                task.inputs.files(
                    buildUnsignedEnclaveTask.outputEnclave,
                    generateEnclaveConfigTask.outputConfigFile,
                    enclaveExtension.signingMaterial
                )
                task.inputEnclave.set(buildUnsignedEnclaveTask.outputEnclave)
                task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
                task.signatureDate.set(enclaveExtension.signatureDate)
                task.outputSigningMaterial.set(enclaveExtension.signingMaterial)
            }

            val addEnclaveSignatureTask = target.createTask<AddEnclaveSignature>(
                "addEnclaveSignature$type",
                this,
                linuxExec
            ) { task ->
                    /**
                     * Setting a dependency on a task (at least a `Copy` task) doesn't mean we'll be depending on the task's output.
                     * Despite the dependency task running when out of date, the dependent task would then be considered up-to-date,
                     * even when declaring `dependsOn`.
                     */
                    task.inputs.files(
                        generateEnclaveSigningMaterialTask.inputEnclave,
                        generateEnclaveSigningMaterialTask.outputSigningMaterial,
                        generateEnclaveConfigTask.outputConfigFile,
                        enclaveExtension.mrsignerPublicKey,
                        enclaveExtension.mrsignerSignature
                    )
                    task.inputEnclave.set(generateEnclaveSigningMaterialTask.inputEnclave)
                    task.inputSigningMaterial.set(generateEnclaveSigningMaterialTask.outputSigningMaterial)
                    task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
                    task.inputMrsignerPublicKey.set(enclaveExtension.mrsignerPublicKey.map {
                        if (!it.asFile.exists()) {
                            throwMissingFileForExternalSigning("mrsignerPublicKey")
                        }
                        it
                    })
                    task.inputMrsignerSignature.set(enclaveExtension.mrsignerSignature.map {
                        if (!it.asFile.exists()) {
                            throwMissingFileForExternalSigning("mrsignerSignature")
                        }
                        it
                    })
                    task.outputSignedEnclave.set(enclaveDirectory.resolve("enclave.signed.so").toFile())
                }

            val generateEnclaveMetadataTask = target.createTask<GenerateEnclaveMetadata>(
                "generateEnclaveMetadata$type",
                this,
                type,
                linuxExec
            ) { task ->
                    val signingTask = enclaveExtension.signingType.map {
                        when (it) {
                            SigningType.DummyKey -> signEnclaveWithKeyTask
                            SigningType.PrivateKey -> signEnclaveWithKeyTask
                            else -> addEnclaveSignatureTask
                        }
                    }
                    task.dependsOn(signingTask)
                    val signedEnclaveFile = enclaveExtension.signingType.flatMap {
                        when (it) {
                            SigningType.DummyKey -> signEnclaveWithKeyTask.outputSignedEnclave
                            SigningType.PrivateKey -> signEnclaveWithKeyTask.outputSignedEnclave
                            else -> {
                                if (!enclaveExtension.mrsignerPublicKey.isPresent) {
                                    throwMissingConfigForExternalSigning("mrsignerPublicKey")
                                }
                                if (!enclaveExtension.mrsignerSignature.isPresent) {
                                    throwMissingConfigForExternalSigning("mrsignerSignature")
                                }
                                addEnclaveSignatureTask.outputSignedEnclave
                            }
                        }
                    }
                    task.inputSignedEnclave.set(signedEnclaveFile)
                    task.inputs.files(signedEnclaveFile)
                }

            val buildSignedEnclaveTask = target.createTask<BuildSignedEnclave>("buildSignedEnclave$type") { task ->
                task.dependsOn(generateEnclaveMetadataTask)
                task.inputs.files(generateEnclaveMetadataTask.inputSignedEnclave)
                task.outputSignedEnclave.set(generateEnclaveMetadataTask.inputSignedEnclave)
            }

            class RenameSignedEnclaveJarTask : Action<Task> {
                override fun execute(task: Task) {
                    val jarTask = task as Jar
                    val enclaveClassName = enclaveClassNameTask.outputEnclaveClassName.get()
                    jarTask.into(enclaveClassName.substringBeforeLast('.').replace('.', '/'))  // Package location
                    jarTask.rename {
                        "${enclaveClassName.substringAfterLast('.')}-$typeLowerCase.signed.so"
                    }
                }
            }

            val signedEnclaveJarTask = target.tasks.register("signedEnclave${type}Jar", Jar::class.java) { task ->
                task.group = CONCLAVE_GROUP
                task.description = "Compile an ${type}-mode enclave that can be loaded by SGX."
                task.dependsOn(enclaveClassNameTask)
                task.archiveAppendix.set("signed-so")
                task.archiveClassifier.set(typeLowerCase)
                // buildSignedEnclaveTask determines which of the three Conclave supported signing methods
                // to use to sign the enclave and invokes the correct task accordingly.
                task.from(buildSignedEnclaveTask.outputSignedEnclave)
                task.doFirst(RenameSignedEnclaveJarTask())
            }

            target.afterEvaluate {
                try {
                    when (RuntimeType.fromString(conclaveExtension.runtime.get())) {
                        RuntimeType.Gramine -> {
                            target.artifacts.add(typeLowerCase, signedEnclaveGramineJarTask.get().archiveFile)
                        }
                        RuntimeType.GraalVM -> {
                            target.artifacts.add(typeLowerCase, signedEnclaveJarTask.get().archiveFile)
                        }
                    }
                } catch (e: Exception) {
                    /** Do nothing. Error condition is handled in [apply]. */
                }
            }
        }
    }

    private fun throwMissingFileForExternalSigning(config: String): Nothing {
        throw GradleException(
            "Your enclave is configured to be signed with an external key but the file specified by '$config' in " +
                    "your build.gradle does not exist. Refer to " +
                    "https://docs.conclave.net/signing.html#generating-keys-for-signing-an-enclave for instructions " +
                    "on how to sign your enclave."
        )
    }

    private fun throwMissingConfigForExternalSigning(config: String): Nothing {
        throw GradleException(
            "Your enclave is configured to be signed with an external key but the configuration '$config' in your " +
                    "build.gradle does not exist. Refer to " +
                    "https://docs.conclave.net/signing.html#how-to-configure-signing-for-your-enclaves for " +
                    "instructions on how to configure signing your enclave."
        )
    }

    private fun checkGradleVersionCompatibility(target: Project) {
        val gradleVersion = target.gradle.gradleVersion
        if (VersionNumber.parse(gradleVersion).baseVersion < VersionNumber(5, 6, 4, null)) {
            throw GradleException("Project ${target.name} is using Gradle version $gradleVersion but the Conclave " +
                    "plugin requires at least version 5.6.4.")
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
}
