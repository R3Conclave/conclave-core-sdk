package com.r3.conclave.plugin.enclave.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.conclave.common.internal.PluginUtils.ENCLAVE_BUNDLES_PATH
import com.r3.conclave.common.internal.PluginUtils.ENCLAVE_PROPERTIES
import com.r3.conclave.common.internal.PluginUtils.GRAALVM_BUNDLE_NAME
import com.r3.conclave.common.internal.PluginUtils.GRAMINE_BUNDLE_NAME
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask.Companion.CONCLAVE_GROUP
import com.r3.conclave.plugin.enclave.gradle.GradleEnclavePlugin.RuntimeType.GRAALVM
import com.r3.conclave.plugin.enclave.gradle.GradleEnclavePlugin.RuntimeType.GRAMINE
import com.r3.conclave.plugin.enclave.gradle.gramine.GenerateGramineBundle
import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.*
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.util.VersionNumber
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import java.util.stream.Collectors.toList
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.name
import kotlin.io.path.setPosixFilePermissions

class GradleEnclavePlugin @Inject constructor(private val layout: ProjectLayout) : Plugin<Project> {
    companion object {
        private const val CONCLAVE_GRAALVM_VERSION = "22.0.0.2-1.4-SNAPSHOT"

        private val CONCLAVE_SDK_VERSION = retrievePackageVersionFromManifest("Conclave-Version")

        fun retrievePackageVersionFromManifest(packageName: String): String {
            return GradleEnclavePlugin::class.java.classLoader
                .getResources(MANIFEST_NAME)
                .asSequence()
                .mapNotNull { it.openStream().use(::Manifest).mainAttributes.getValue(packageName) }
                .firstOrNull() ?: throw IllegalStateException("Could not find $packageName in plugin's manifest")
        }
    }

    private enum class RuntimeType {
        GRAMINE,
        GRAALVM;
    }

    private var pythonSourcePath: Path? = null
    private lateinit var runtimeType: Provider<RuntimeType>

    override fun apply(target: Project) {
        checkGradleVersionCompatibility(target)
        target.logger.info("Applying Conclave gradle plugin for version $CONCLAVE_SDK_VERSION")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions(target)

        target.pluginManager.apply(JavaPlugin::class.java)
        target.pluginManager.apply(ShadowPlugin::class.java)

        val conclaveExtension = target.extensions.create("conclave", ConclaveExtension::class.java)

        val sourcePaths = Files.list(target.projectDir.toPath() / "src" / "main").use { it.collect(toList()) }
        pythonSourcePath = if (sourcePaths.size == 1 && sourcePaths[0].name == "python") sourcePaths[0] else null

        // Parse the runtime string into the enum and then make sure it's consistent with whether this project is
        // Python or not.
        runtimeType = conclaveExtension.runtime
            // Provider.map is not called if the upstream value is not set (i.e. null), but we want execute for null,
            // so we supply a token string for null
            .convention(" null ")
            .map { string ->
                val enum = try {
                    if (string == " null ") null else RuntimeType.valueOf(string.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw GradleException(
                        "'${conclaveExtension.runtime.get()}' is not a valid enclave runtime type.\n" +
                                "Valid runtime types are: ${RuntimeType.values().joinToString { it.name.lowercase() }}.")
                }
                if (pythonSourcePath != null) {
                    if (enum == GRAALVM) {
                        // The user has explicitly specified GraalVM whilst also intending to have Python code.
                        throw GradleException("Python enclave with GraalVM not supported. Use 'gramine' instead.")
                    }
                    GRAMINE  // Python projects must always use Gramine
                } else {
                    enum ?: GRAALVM
                }
            }

        target.afterEvaluate {
            // This is called before the build tasks are executed but after the build.gradle file
            // has been parsed. This gives us an opportunity to perform actions based on the user configuration
            // of the enclave.

            // If language support is enabled then automatically add the required dependency.
            if (runtimeType.get() == GRAALVM && conclaveExtension.supportLanguages.get().isNotEmpty()) {
                // It might be possible that the conclave part of the version not match the current version, e.g. if
                // SDK is 1.4-SNAPSHOT but we're still using 20.0.0.2-1.3 because we've not had the need to update
                target.dependencies.add("implementation", "com.r3.conclave:graal-sdk:$CONCLAVE_GRAALVM_VERSION")
            }
            // Add dependencies automatically (so developers don't have to)
            target.dependencies.add("implementation", "com.r3.conclave:conclave-enclave:$CONCLAVE_SDK_VERSION")
            target.dependencies.add("testImplementation", "com.r3.conclave:conclave-host:$CONCLAVE_SDK_VERSION")
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

        target.createTask<EnclaveClassName>("enclaveClassName") { task ->
            task.dependsOn(target.tasks.withType(JavaCompile::class.java))
            task.inputClassPath.set(getMainSourceSet(target).runtimeClasspath)
        }

        val generateEnclavePropertiesTask = target.createTask<GenerateEnclaveProperties>(
            "generateEnclaveProperties",
        ) {
            it.conclaveExtension.set(conclaveExtension)
            it.enclavePropertiesFile.set(baseDirectory.resolve(ENCLAVE_PROPERTIES).toFile())
        }

        val enclaveFatJarTask = if (pythonSourcePath == null) {
            target.tasks.withType(ShadowJar::class.java).getByName("shadowJar")
        } else {
            // This is a bit hacky. It essentially "converts" the pre-compiled adapter jar into a Gradle Jar task.
            target.createTask<Jar>("pythonEnclaveAdapterJar") { task ->
                task.first {
                    val adapterJar = task.temporaryDir.resolve("python-enclave-adapter.jar").toPath()
                    javaClass.copyResource("/python-support/enclave-adapter.jar", adapterJar)
                    task.from(target.zipTree(adapterJar))
                }
            }
        }

        enclaveFatJarTask.makeReproducible()
        enclaveFatJarTask.archiveAppendix.set("fat")
        enclaveFatJarTask.from(generateEnclavePropertiesTask.enclavePropertiesFile) { copySpec ->
            enclaveFatJarTask.onEnclaveClassName { enclaveClassName ->
                copySpec.into(enclaveClassName.substringBeforeLast('.').replace('.', '/'))
                copySpec.rename { ENCLAVE_PROPERTIES }
            }
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
        createMockArtifact(target, enclaveFatJarTask)

        // Create the tasks that are required build the Release, Debug and Simulation artifacts.
        createEnclaveArtifacts(target, conclaveExtension, enclaveFatJarTask)
    }

    private fun createGenerateGramineBundleTask(
            target: Project,
            type: BuildType,
            conclaveExtension: ConclaveExtension,
            enclaveFatJarTask: Jar,
            signingKey: Provider<RegularFile?>
    ): GenerateGramineBundle {
        return target.createTask("generateGramine${type}Bundle", type) { task ->
            // TODO: Build Gramine enclaves in conclave-build container: https://r3-cev.atlassian.net/browse/CON-1229
            task.signingKey.set(signingKey)
            task.productId.set(conclaveExtension.productID)
            task.revocationLevel.set(conclaveExtension.revocationLevel)
            task.maxThreads.set(conclaveExtension.maxThreads)
            task.enclaveJar.set(enclaveFatJarTask.archiveFile)
            if (pythonSourcePath != null) {
                val pythonFiles = target.fileTree(pythonSourcePath).files
                task.pythonFile.set(pythonFiles.first())
            }
            task.outputDir.set((baseDirectory / type.name.lowercase() / "gramine-bundle").toFile())
        }
    }

    private fun createGramineBundleZipTask(
        target: Project,
        type: BuildType,
        generateGramineBundleTask: GenerateGramineBundle
    ): TaskProvider<Zip> {
        return target.tasks.register("gramine${type}BundleZip", Zip::class.java) { task ->
            // No need to do any compression here, we're only using zip as a container. The compression will be done
            // by the containing jar.
            task.entryCompression = STORED
            task.from(generateGramineBundleTask.outputDir)
            task.destinationDirectory.set((baseDirectory / type.name.lowercase()).toFile())
            task.archiveBaseName.set("gramine-bundle")
        }
    }

    fun signToolPath(): Path = getSgxTool("sgx_sign")

    fun ldPath(): Path = getSgxTool("ld")

    private fun getSgxTool(name: String): Path {
        val path = baseDirectory / "sgx-tools" / name
        if (!path.exists()) {
            javaClass.copyResource("/sgx-tools/$name", path)
            path.setPosixFilePermissions(path.getPosixFilePermissions() + OWNER_EXECUTE)
        }
        return path
    }

    private val baseDirectory: Path by lazy { layout.buildDirectory.get().asFile.toPath() / "conclave" }
    private val gramineBuildDirectory: Path by lazy { baseDirectory.resolve("gramine") }

    /**
     * Get the main source set for a given project
     */
    private fun getMainSourceSet(project: Project): SourceSet {
        return (project.properties["sourceSets"] as SourceSetContainer).getByName("main")
    }

    private fun createMockArtifact(target: Project, enclaveFatJar: Jar) {
        // Mock mode does not require all the enclave building tasks. The enclave Jar file is just packaged
        // as an artifact.
        target.artifacts.add("mock", enclaveFatJar.archiveFile)
    }

    private fun createEnclaveArtifacts(target: Project, conclaveExtension: ConclaveExtension, enclaveFatJarTask: Jar) {
        // Dummy key
        val createDummyKeyTask = target.createTask<GenerateDummyMrsignerKey>("createDummyKey") { task ->
            task.outputKey.set(baseDirectory.resolve("dummy_key.pem").toFile())
        }

        val linkerScriptFile = baseDirectory.resolve("Enclave.lds")

        val generateReflectionConfigTask =
            target.createTask<GenerateReflectionConfig>("generateReflectionConfig") { task ->
                val enclaveClassNameTask = target.tasks.withType(EnclaveClassName::class.java).single()
                task.dependsOn(enclaveClassNameTask)
                task.enclaveClass.set(enclaveClassNameTask.outputEnclaveClassName)
                task.reflectionConfig.set(baseDirectory.resolve("reflectconfig").toFile())
            }

        val generateAppResourcesConfigTask =
            target.createTask<GenerateAppResourcesConfig>("generateAppResourcesConfig") { task ->
                task.jarFile.set(enclaveFatJarTask.archiveFile)
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
            task.tag.set("conclave-build:$CONCLAVE_SDK_VERSION")
            // Create a 'latest' tag too so users can follow our tutorial documentation using the
            // tag 'conclave-build:latest' rather than looking up the conclave version.
            task.tagLatest.set("conclave-build:latest")
            task.buildInDocker.set(conclaveExtension.buildInDocker)
        }

        for (type in BuildType.values().filter { it != BuildType.Mock }) {
            val typeLowerCase = type.name.lowercase()

            val enclaveExtension = when (type) {
                BuildType.Release -> conclaveExtension.release
                BuildType.Debug -> conclaveExtension.debug
                BuildType.Simulation -> conclaveExtension.simulation
                else -> throw IllegalStateException()
            }

            val signingKey = enclaveExtension.signingType.flatMap {
                when (it) {
                    SigningType.DummyKey -> createDummyKeyTask.outputKey
                    SigningType.PrivateKey -> enclaveExtension.signingKey
                    else -> target.provider { null }
                }
            }

            val enclaveDirectory = baseDirectory.resolve(typeLowerCase)

            // Gramine related tasks
            val generateGramineBundleTask = createGenerateGramineBundleTask(
                target,
                type,
                conclaveExtension,
                enclaveFatJarTask,
                signingKey
            )
            val gramineBundleZipTask = createGramineBundleZipTask(target, type, generateGramineBundleTask)

            // GraalVM related tasks

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
                task.jarFile.set(enclaveFatJarTask.archiveFile)
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
                    task.inputKey.set(signingKey)
                    task.outputSignedEnclave.set(enclaveDirectory.resolve("enclave.signed.so").toFile())
                    task.buildInDocker.set(conclaveExtension.buildInDocker)
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
                )
                task.buildInDocker.set(conclaveExtension.buildInDocker)
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
                    task.buildInDocker.set(conclaveExtension.buildInDocker)
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
                    task.buildInDocker.set(conclaveExtension.buildInDocker)
                }

            val buildSignedEnclaveTask = target.createTask<BuildSignedEnclave>("buildSignedEnclave$type") { task ->
                task.dependsOn(generateEnclaveMetadataTask)
                task.inputs.files(generateEnclaveMetadataTask.inputSignedEnclave)
                task.outputSignedEnclave.set(generateEnclaveMetadataTask.inputSignedEnclave)
            }

            val enclaveBundleJarTask = target.createTask<Jar>("enclaveBundle${type}Jar") { task ->
                task.group = CONCLAVE_GROUP
                task.description = "Compile an ${type}-mode enclave that can be loaded by SGX."
                task.archiveAppendix.set("bundle")
                task.archiveClassifier.set(typeLowerCase)
                task.makeReproducible()

                val bundleOutput: Provider<RegularFile> = runtimeType.flatMap {
                    when (it) {
                        // buildSignedEnclaveTask determines which of the three Conclave supported signing methods
                        // to use to sign the enclave and invokes the correct task accordingly.
                        GRAALVM -> buildSignedEnclaveTask.outputSignedEnclave
                        GRAMINE -> gramineBundleZipTask.get().archiveFile
                        else -> throw IllegalArgumentException()
                    }
                }
                task.from(bundleOutput)

                task.rename {
                    val bundleName = when (runtimeType.get()) {
                        GRAALVM -> GRAALVM_BUNDLE_NAME
                        GRAMINE -> GRAMINE_BUNDLE_NAME
                        else -> throw IllegalArgumentException()
                    }
                    "$typeLowerCase-$bundleName"
                }

                task.onEnclaveClassName { enclaveClassName ->
                    task.into("$ENCLAVE_BUNDLES_PATH/$enclaveClassName")
                }
            }

            target.artifacts.add(typeLowerCase, enclaveBundleJarTask.archiveFile)
        }
    }

    private fun Jar.makeReproducible() {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        isZip64 = true
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

    private fun autoconfigureDependencyVersions(target: Project) {
        target.configurations.all { configuration ->
            configuration.withDependencies { dependencySet ->
                dependencySet
                        .filterIsInstance<ExternalDependency>()
                        .filter { it.group == "com.r3.conclave" && it.version.isNullOrEmpty() }
                        .forEach { dep ->
                            dep.version {
                                it.require(CONCLAVE_SDK_VERSION)
                            }
                        }
            }
        }
    }

    /**
     * Helper method to perform some action when the enclave class name is available.
     */
    private fun Task.onEnclaveClassName(block: (String) -> Unit) {
        val enclaveClassNameTask = project.tasks.withType(EnclaveClassName::class.java).singleOrNull()
        checkNotNull(enclaveClassNameTask) {
            "onEnclaveClassName can only be used after the EnclaveClassName task has been created"
        }
        if (pythonSourcePath == null) {
            dependsOn(enclaveClassNameTask)
            first {
                block(enclaveClassNameTask.outputEnclaveClassName.get())
            }
        } else {
            // This is a bit of a hack, but if the enclave is in Python then we're using the
            // PythonEnclaveAdapter class.
            first {
                block("com.r3.conclave.python.PythonEnclaveAdapter")
            }
        }
    }

    // Hack to get around Gradle warning on Java lambdas: https://docs.gradle.org/7.3.1/userguide/validation_problems.html#implementation_unknown
    private fun Task.first(block: () -> Unit) {
        doFirst(ActionWrapper(block))
    }

    private class ActionWrapper(private val block: () -> Unit) : Action<Task> {
        override fun execute(t: Task) = block()
    }

    private inline fun <reified T : Task> Project.createTask(name: String, vararg constructorArgs: Any?, configure: (T) -> Unit): T {
        val task = tasks.create(name, T::class.java, *constructorArgs)
        configure(task)
        return task
    }
}
