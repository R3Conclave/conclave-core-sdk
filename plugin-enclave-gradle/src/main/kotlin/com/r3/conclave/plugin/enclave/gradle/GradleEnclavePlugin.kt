package com.r3.conclave.plugin.enclave.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.conclave.common.internal.PluginUtils
import com.r3.conclave.plugin.enclave.gradle.GradleEnclavePlugin.RuntimeType.GRAALVM
import com.r3.conclave.plugin.enclave.gradle.GradleEnclavePlugin.RuntimeType.GRAMINE
import com.r3.conclave.plugin.enclave.gradle.graalvm.CopyGraalVM
import com.r3.conclave.plugin.enclave.gradle.graalvm.GenerateAppResourcesConfig
import com.r3.conclave.plugin.enclave.gradle.graalvm.GenerateReflectionConfig
import com.r3.conclave.plugin.enclave.gradle.graalvm.NativeImage
import com.r3.conclave.plugin.enclave.gradle.gramine.GenerateGramineManifest
import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.ZipEntryCompression.STORED
import org.gradle.util.VersionNumber
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import java.util.stream.Collectors.toList
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.name
import kotlin.io.path.setPosixFilePermissions

class GradleEnclavePlugin @Inject constructor(private val projectLayout: ProjectLayout) : Plugin<Project> {
    companion object {
        private const val CONCLAVE_GRAALVM_VERSION = "22.0.0.2-1.3"

        private val CONCLAVE_SDK_VERSION = run {
            GradleEnclavePlugin::class.java.classLoader
                .getResources(MANIFEST_NAME)
                .asSequence()
                .mapNotNull { it.openStream().use(::Manifest).mainAttributes.getValue("Conclave-Version") }
                .firstOrNull() ?: throw IllegalStateException("Could not find Conclave-Version in plugin's manifest")
        }
    }

    private enum class RuntimeType {
        GRAMINE,
        GRAALVM;
    }

    private lateinit var project: Project
    private var pythonSourcePath: Path? = null
    private lateinit var runtimeType: Provider<RuntimeType>

    override fun apply(target: Project) {
        project = target
        checkGradleVersionCompatibility()
        target.logger.info("Applying Conclave gradle plugin for version $CONCLAVE_SDK_VERSION")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions()

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

        val sourceFatJar: Provider<RegularFile> = if (pythonSourcePath == null) {
            // For non-Python enclaves, build a first-pass fat jar of the enclave code.
            target.tasks.withType(ShadowJar::class.java).getByName("shadowJar").archiveFile
        } else {
            // For Python enclaves, take the pre-compiled Python adapter enclave jar
            val pythonAdapterJar = file("python-enclave-adapter.jar")
            javaClass.copyResource("/python-support/enclave-adapter.jar", pythonAdapterJar.get().asFile.toPath())
            pythonAdapterJar
        }

        val enclaveClassNameTask = createTask<EnclaveClassName>("enclaveClassName") { task ->
            task.sourceFatJar.set(sourceFatJar)
            task.enclaveClassNameFile.set(file("enclave-class-name.txt"))
        }

        val generateEnclavePropertiesTask = createTask<GenerateEnclaveProperties>(
            "generateEnclaveProperties",
            conclaveExtension
        ) { task ->
            task.enclavePropertiesFile.set(file(PluginUtils.ENCLAVE_PROPERTIES))
        }

        // Take the source fat jar and create a new with additional resources added to it
        val enclaveFatJarTask = createTask<EnclaveFatJar>("enclaveFatJar") { task ->
            task.sourceFatJar.set(sourceFatJar)
            val configuringTask = task.delayedConfiguration()
            configuringTask.enclaveProperties.set(generateEnclavePropertiesTask.enclavePropertiesFile)
            configuringTask.enclaveClassName.set(enclaveClassNameTask.enclaveClassName())
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
        createMockArtifact(enclaveFatJarTask)

        // Create the tasks that are required build the Release, Debug and Simulation artifacts.
        createNonMockArtifacts(conclaveExtension, enclaveFatJarTask, enclaveClassNameTask)
    }

    private fun createMockArtifact(enclaveFatJarTask: EnclaveFatJar) {
        // Mock mode does not require all the enclave building tasks. The enclave Jar file is just packaged
        // as an artifact.
        project.artifacts.add("mock", enclaveFatJarTask.archiveFile)
    }

    private fun createNonMockArtifacts(
        conclaveExtension: ConclaveExtension,
        enclaveFatJarTask: EnclaveFatJar,
        enclaveClassNameTask: EnclaveClassName
    ) {
        // Dummy key
        val createDummyKeyTask = createTask<GenerateDummyMrsignerKey>("createDummyKey") { task ->
            task.outputKey.set(file("dummy_key.pem"))
        }

        val generateReflectionConfigTask = createTask<GenerateReflectionConfig>("generateReflectionConfig") { task ->
            task.enclaveClassName.set(enclaveClassNameTask.enclaveClassName())
            task.reflectionConfig.set(file("reflectconfig"))
        }

        val generateAppResourcesConfigTask =
            createTask<GenerateAppResourcesConfig>("generateAppResourcesConfig") { task ->
                task.jarFile.set(enclaveFatJarTask.archiveFile)
                task.appResourcesConfigFile.set(file("app-resources-config.json"))
            }

        val copyGraalVM = createTask<CopyGraalVM>("copyGraalVM") { task ->
            // Create a configuration for downloading graalvm-*.tar.gz using Gradle
            val graalVMConfigName = "${task.name}Config"
            val configuration = project.configurations.create(graalVMConfigName)
            project.dependencies.add(graalVMConfigName, "com.r3.conclave:graalvm:$CONCLAVE_GRAALVM_VERSION@tar.gz")
            task.configuration.set(configuration)
            task.distributionDir.set(dir("graalvm"))
        }

        val linuxExec = createTask<LinuxExec>("setupLinuxExecEnvironment") { task ->
            task.baseDirectory.set(project.projectDir.toPath().toString())
            task.tag.set("conclave-build:$CONCLAVE_SDK_VERSION")
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

            // Simulation and debug default to using a dummy key. Release defaults to external key
            val keyType = when (type) {
                BuildType.Release -> SigningType.ExternalKey
                else -> SigningType.DummyKey
            }
            enclaveExtension.signingType.set(keyType)

            // Set the default signing material location as an absolute path because if the
            // user overrides it they will use a project relative (rather than build directory
            // relative) path name.
            enclaveExtension.signingMaterial.set(projectLayout.buildDirectory.file("enclave/$type/signing_material.bin"))

            val gramineZipBundleTask = createGramineTasks(type, enclaveFatJarTask)
            val signedGraalVMEnclaveFile = createGraalVmTasks(
                type,
                conclaveExtension,
                enclaveExtension,
                linuxExec,
                copyGraalVM,
                enclaveFatJarTask,
                generateReflectionConfigTask,
                generateAppResourcesConfigTask,
                createDummyKeyTask
            )

            val enclaveBundleJarTask = createTask<EnclaveBundleJar>("enclaveBundle${type}Jar", type) { task ->
                // Based on the runtime config the relevant tasks will be executed. runtimeType.flatMap and
                // runtimeType.map below automatically wire up the task dependencies so there's no need to manually
                // call dependsOn.
                task.bundleFile.set(runtimeType.flatMap {
                    when (it) {
                        GRAALVM -> signedGraalVMEnclaveFile
                        GRAMINE -> gramineZipBundleTask.get().archiveFile
                        null -> throw IllegalStateException()  // Keep the compiler happy
                    }
                })
                task.fileName.set(runtimeType.map {
                    val bundleName = when (it) {
                        GRAALVM -> PluginUtils.GRAALVM_BUNDLE_NAME
                        GRAMINE -> PluginUtils.GRAMINE_BUNDLE_NAME
                        null -> throw IllegalStateException()  // Keep the compiler happy
                    }
                    "$typeLowerCase-$bundleName"
                })
                task.enclaveClassName.set(enclaveClassNameTask.enclaveClassName())
            }

            project.artifacts.add(typeLowerCase, enclaveBundleJarTask.archiveFile)
        }
    }

    private fun createGraalVmTasks(
        type: BuildType,
        conclaveExtension: ConclaveExtension,
        enclaveExtension: EnclaveExtension,
        linuxExec: LinuxExec,
        copyGraalVM: CopyGraalVM,
        enclaveFatJarTask: EnclaveFatJar,
        generateReflectionConfigTask: GenerateReflectionConfig,
        generateAppResourcesConfigTask: GenerateAppResourcesConfig,
        createDummyKeyTask: GenerateDummyMrsignerKey
    ): Provider<RegularFile> {
        fun buildFile(path: String): Provider<RegularFile> = file("${type.name.lowercase()}/$path")

        val buildUnsignedGraalEnclaveTask = createTask<NativeImage>(
            "buildUnsignedGraalEnclave$type",
            this,
            type,
            linuxExec
        ) { task ->
            task.dependsOn(linuxExec)
            task.nativeImagePath.set(copyGraalVM.distributionDir)
            task.jarFile.set(enclaveFatJarTask.archiveFile)
            task.reflectionConfiguration.set(generateReflectionConfigTask.reflectionConfig)
            task.appResourcesConfig.set(generateAppResourcesConfigTask.appResourcesConfigFile)
            task.reflectionConfigurationFiles.from(conclaveExtension.reflectionConfigurationFiles)
            task.serializationConfigurationFiles.from(conclaveExtension.serializationConfigurationFiles)
            task.maxStackSize.set(conclaveExtension.maxStackSize)
            task.maxHeapSize.set(conclaveExtension.maxHeapSize)
            task.supportLanguages.set(conclaveExtension.supportLanguages)
            task.deadlockTimeout.set(conclaveExtension.deadlockTimeout)
            task.outputEnclave.set(buildFile("enclave.so"))
        }

        val generateEnclaveConfigTask = createTask<GenerateEnclaveConfig>("generateEnclaveConfig$type", type) { task ->
            task.productID.set(conclaveExtension.productID)
            task.revocationLevel.set(conclaveExtension.revocationLevel)
            task.maxHeapSize.set(conclaveExtension.maxHeapSize)
            task.maxStackSize.set(conclaveExtension.maxStackSize)
            task.tcsNum.set(conclaveExtension.maxThreads)
            task.outputConfigFile.set(buildFile("enclave.xml"))
        }

        val signEnclaveWithKeyTask = createTask<SignEnclave>(
            "signEnclaveWithKey$type",
            this,
            enclaveExtension,
            type,
            linuxExec
        ) { task ->
            task.dependsOn(linuxExec)
            task.inputEnclave.set(buildUnsignedGraalEnclaveTask.outputEnclave)
            task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
            task.inputKey.set(enclaveExtension.signingType.flatMap {
                when (it) {
                    SigningType.DummyKey -> createDummyKeyTask.outputKey
                    SigningType.PrivateKey -> enclaveExtension.signingKey
                    else -> project.provider { null }
                }
            })
            task.outputSignedEnclave.set(buildFile("enclave.signed.so"))
        }

        val generateEnclaveSigningMaterialTask = createTask<GenerateEnclaveSigningMaterial>(
            "generateEnclaveSigningMaterial$type",
            this,
            linuxExec
        ) { task ->
            task.description = "Generate standalone signing material for a $type mode enclave that can be used " +
                    "with an external signing source."
            task.dependsOn(linuxExec)
            task.inputEnclave.set(buildUnsignedGraalEnclaveTask.outputEnclave)
            task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
            task.signatureDate.set(enclaveExtension.signatureDate)
            task.outputSigningMaterial.set(enclaveExtension.signingMaterial)
        }

        val addEnclaveSignatureTask = createTask<AddEnclaveSignature>(
            "addEnclaveSignature$type",
            this,
            linuxExec
        ) { task ->
            task.dependsOn(linuxExec)
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
            task.outputSignedEnclave.set(buildFile("enclave.signed.so"))
        }

        val signedEnclaveFile: Provider<RegularFile> = enclaveExtension.signingType.flatMap {
            when (it) {
                SigningType.DummyKey -> signEnclaveWithKeyTask.outputSignedEnclave
                SigningType.PrivateKey -> signEnclaveWithKeyTask.outputSignedEnclave
                SigningType.ExternalKey -> addEnclaveSignatureTask.outputSignedEnclave
                null -> throw IllegalStateException()  // Keep the compiler happy
            }
        }

        createTask<GenerateEnclaveMetadata>("generateEnclaveMetadata$type", this, type, linuxExec) { task ->
            task.inputSignedEnclave.set(signedEnclaveFile)
        }

        return signedEnclaveFile
    }

    private fun createGramineTasks(type: BuildType, enclaveFatJarTask: EnclaveFatJar): TaskProvider<Zip> {
        val generateGramineManifestTask = createTask<GenerateGramineManifest>("generateGramineManifest$type") { task ->
            task.manifestFile.set(file("gramine/${PluginUtils.GRAMINE_MANIFEST}"))
        }

        val gramineZipBundle = project.tasks.register("gramine${type}BundleZip", Zip::class.java) { task ->
            // No need to do any compression here, we're only using zip as a container. The compression will be done
            // by the containing jar.
            task.entryCompression = STORED

            if (pythonSourcePath != null) {
                val pythonFiles = project.fileTree(pythonSourcePath!!).files
                if (pythonFiles.size == 1) {
                    task.from(pythonFiles.first()) { copySpec ->
                        copySpec.rename { PluginUtils.PYTHON_FILE }
                    }
                } else {
                    throw GradleException("Only a single Python script is supported, but ${pythonFiles.size} were " +
                            "found in $pythonSourcePath")
                }
            }

            task.from(enclaveFatJarTask.archiveFile) { copySpec ->
                copySpec.rename { PluginUtils.GRAMINE_ENCLAVE_JAR }
            }
            task.from(generateGramineManifestTask.manifestFile) { copySpec ->
                copySpec.rename { PluginUtils.GRAMINE_MANIFEST }
            }
            task.archiveBaseName.set("gramine-bundle")
            task.archiveAppendix.set(type.name.lowercase())
        }

        return gramineZipBundle
    }

    private fun throwMissingFileForExternalSigning(config: String): Nothing {
        throw GradleException(
            "Your enclave is configured to be signed with an external key but the file specified by '$config' in " +
                    "your build.gradle does not exist. Refer to " +
                    "https://docs.conclave.net/signing.html#generating-keys-for-signing-an-enclave for instructions " +
                    "on how to sign your enclave."
        )
    }

    private fun checkGradleVersionCompatibility() {
        val gradleVersion = project.gradle.gradleVersion
        if (VersionNumber.parse(gradleVersion).baseVersion < VersionNumber(5, 6, 4, null)) {
            throw GradleException("Project ${project.name} is using Gradle version $gradleVersion but the Conclave " +
                    "plugin requires at least version 5.6.4.")
        }
    }

    private fun autoconfigureDependencyVersions() {
        project.configurations.all { configuration ->
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

    private fun file(path: String): Provider<RegularFile> = projectLayout.buildDirectory.file("conclave/$path")

    private fun dir(path: String): Provider<Directory> = projectLayout.buildDirectory.dir("conclave/$path")

    fun signToolPath(): Path = getSgxTool("sgx_sign")

    fun ldPath(): Path = getSgxTool("ld")

    private fun getSgxTool(name: String): Path {
        val path = file("sgx-tools/$name").get().asFile.toPath()
        if (!path.exists()) {
            javaClass.copyResource("/sgx-tools/$name", path)
            path.setPosixFilePermissions(path.getPosixFilePermissions() + OWNER_EXECUTE)
        }
        return path
    }

    /**
     * Get the main source set for a given project
     */
    private fun getMainSourceSet(): SourceSet {
        return (project.properties["sourceSets"] as SourceSetContainer).getByName("main")
    }

    private inline fun <reified T : Task> createTask(name: String, vararg constructorArgs: Any?, configure: (T) -> Unit): T {
        val task = project.tasks.create(name, T::class.java, *constructorArgs)
        configure(task)
        return task
    }
}
