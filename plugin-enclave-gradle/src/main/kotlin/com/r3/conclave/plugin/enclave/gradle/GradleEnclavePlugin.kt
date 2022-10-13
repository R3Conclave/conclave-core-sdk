package com.r3.conclave.plugin.enclave.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.r3.conclave.plugin.enclave.gradle.ConclaveTask.Companion.CONCLAVE_GROUP
import com.r3.conclave.plugin.enclave.gradle.gramine.BuildUnsignedGramineEnclave
import com.r3.conclave.plugin.enclave.gradle.gramine.GramineLogType
import org.gradle.api.*
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.file.Directory
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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

    private lateinit var target: Project
    private val baseDirectory: Path by lazy { layout.buildDirectory.get().asFile.toPath() / "conclave" }
    val graalVMDistributionPath: String by lazy { "$baseDirectory/graalvm" }

    override fun apply(target: Project) {
        //  This is purely to allow private functions to avoid passing around Target as parameters
        this.target = target
        checkGradleVersionCompatibility()

        val sdkVersion = readVersionFromPluginManifest()
        target.logger.info("Applying Conclave gradle plugin for version $sdkVersion")

        // Allow users to specify the enclave dependency like this: implementation "com.r3.conclave:conclave-enclave"
        autoconfigureDependencyVersions(sdkVersion)

        this.target.pluginManager.apply(JavaPlugin::class.java)
        this.target.pluginManager.apply(ShadowPlugin::class.java)

        // Create configurations for each of the build types.
        for (type in BuildType.values()) {
            // https://docs.gradle.org/current/userguide/cross_project_publications.html
            target.configurations.create(type.name.lowercase()) {
                it.isCanBeConsumed = true
                it.isCanBeResolved = false
            }
        }

        val conclaveExtension = this.target.extensions.create("conclave", ConclaveExtension::class.java)

        // Tasks common to all configurations
        val enclaveClassNameTask = enclaveClassNameTask()

        val generateEnclavePropertiesTask = generateEnclavePropertiesTask(enclaveClassNameTask, conclaveExtension)

        val shadowJarTask = shadowJarTask(generateEnclavePropertiesTask)

        createMockArtifact(shadowJarTask)

        // Create the tasks that are required build the Release, Debug and Simulation artifacts.
        createEnclaveArtifacts(
            sdkVersion,
            conclaveExtension,
            shadowJarTask,
            enclaveClassNameTask
        )

        this.target.afterEvaluate {

            this.target.logger.info("Runtime Type: ${conclaveExtension.runtime.get()}")

            // This is called before the build tasks are executed but after the build.gradle file
            // has been parsed. This gives us an opportunity to perform actions based on the user configuration
            // of the enclave.
            // If language support is enabled then automatically add the required dependency.
            if (conclaveExtension.supportLanguages.get()
                    .isNotEmpty() && conclaveExtension.runtime.get() == RuntimeType.Graal
            ) {
                // It might be possible that the conclave part of the version not match the current version, e.g. if
                // SDK is 1.4-SNAPSHOT but we're still using 20.0.0.2-1.3 because we've not had the need to update
                this.target.dependencies.add("implementation", "com.r3.conclave:graal-sdk:$CONCLAVE_GRAALVM_VERSION")
            }
            // Add dependencies automatically (so developers don't have to)
            this.target.dependencies.add("implementation", "com.r3.conclave:conclave-enclave:$sdkVersion")
            this.target.dependencies.add("testImplementation", "com.r3.conclave:conclave-host:$sdkVersion")
            // Make sure that the user has specified productID, print friendly error message if not
            if (!conclaveExtension.productID.isPresent) {
                throw GradleException(
                    "Enclave product ID not specified! " +
                            "Please set the 'productID' property in the build configuration for your enclave.\n" +
                            "If you're unsure what this error message means, please consult the conclave documentation."
                )
            }
            // Make sure that the user has specified revocationLevel, print friendly error message if not
            if (!conclaveExtension.revocationLevel.isPresent) {
                throw GradleException(
                    "Enclave revocation level not specified! " +
                            "Please set the 'revocationLevel' property in the build configuration for your enclave.\n" +
                            "If you're unsure what this error message means, please consult the conclave documentation."
                )
            }
        }
    }


    private fun enclaveClassNameTask(): EnclaveClassName {
        return this.target.createTask("enclaveClassName") { task ->
            task.dependsOn(target.tasks.withType(JavaCompile::class.java))
            task.inputClassPath.set(getMainSourceSet(this.target).runtimeClasspath)
        }
    }

    private fun generateEnclavePropertiesTask(
        enclaveClassNameTask: EnclaveClassName,
        conclaveExtension: ConclaveExtension
    ): GenerateEnclaveProperties {
        return this.target.createTask<GenerateEnclaveProperties>("generateEnclaveProperties") { task ->
            task.dependsOn(enclaveClassNameTask)
            task.resourceDirectory.set(getMainSourceSet(target).output.resourcesDir.toString())
            task.mainClassName.set(enclaveClassNameTask.outputEnclaveClassName)
            task.conclaveExtension.set(conclaveExtension)
        }
    }

    private fun shadowJarTask(generateEnclavePropertiesTask: GenerateEnclaveProperties): ShadowJar {
        return this.target.tasks.withType(ShadowJar::class.java).getByName("shadowJar") { task ->
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
    }

    fun signToolPath(): Path = getSgxTool("sgx_sign")

    fun ldPath(): Path = getSgxTool("ld")

    private fun getSgxTool(name: String): Path {
        val path = baseDirectory / "sgx-tools" / name
        if (!path.exists()) {
            path.parent.createDirectories()
            javaClass.getResourceAsStream("/sgx-tools/$name")!!.use {
                Files.copy(it, path, REPLACE_EXISTING)
            }
            path.setPosixFilePermissions(path.getPosixFilePermissions() + OWNER_EXECUTE)
        }
        return path
    }

    /**
     * Get the main source set for a given project
     */
    private fun getMainSourceSet(project: Project): SourceSet {
        return (project.properties["sourceSets"] as SourceSetContainer).getByName("main")
    }

    private fun createMockArtifact(shadowJarTask: ShadowJar) {
        // Mock mode does not require all the enclave building tasks. The enclave Jar file is just packaged
        // as an artifact.
        target.artifacts.add("mock", shadowJarTask.archiveFile)
    }

    private fun createEnclaveArtifacts(
        sdkVersion: String,
        conclaveExtension: ConclaveExtension,
        shadowJarTask: ShadowJar,
        enclaveClassNameTask: EnclaveClassName
    ) {
        val runtimeType = conclaveExtension.runtime.get()
        //  Tasks common to all build configurations
        val generateDummyMrsignerKey = generateDummyMrsignerKey()

        val generateReflectionConfigTask = generateReflectionConfigTask(enclaveClassNameTask)

        val generateAppResourcesConfigTask = generateAppResourcesConfigTask(shadowJarTask)

        val copyGraalVM = copyGraalVM()

        val linuxExec = linuxExec(sdkVersion)

        target.logger.info("Runtime Type: $runtimeType")
        linuxExec.dependsOn(copyGraalVM)

        //  Here we loop through each configuration
        for (type in BuildType.values().filter { it != BuildType.Mock }) {
            //  This is just a helper to share some common variables used in the private functions
            val taskHelper = TaskHelper(type, conclaveExtension, layout.buildDirectory.get())

            // Gramine related tasks
            val buildUnsignedGramineEnclaveTask = taskHelper.buildUnsignedGramineEnclaveTask(shadowJarTask)

            val buildGramineEnclaveJarTask = taskHelper.buildGramineEnclaveJar(
                enclaveClassNameTask,
                shadowJarTask,
                buildUnsignedGramineEnclaveTask
            )

            // Graal related tasks
            val buildUnsignedGraalEnclaveTask = taskHelper.buildUnsignedGraalEnclaveTask(
                shadowJarTask,
                generateReflectionConfigTask,
                generateAppResourcesConfigTask,
                copyGraalVM,
                linuxExec
            )
            val buildUnsignedEnclaveTask = taskHelper.buildUnsignedEnclaveTask(buildUnsignedGraalEnclaveTask)

            val generateEnclaveConfigTask = taskHelper.generateEnclaveConfigTask()

            val signEnclaveWithKeyTask = taskHelper.signEnclaveWithKeyTask(
                buildUnsignedEnclaveTask,
                generateEnclaveConfigTask,
                generateDummyMrsignerKey,
                linuxExec
            )

            val generateEnclaveSigningMaterialTask = taskHelper.generateEnclaveSigningMaterialTask(
                buildUnsignedEnclaveTask,
                generateEnclaveConfigTask,
                linuxExec
            )

            val addEnclaveSignatureTask = taskHelper.addEnclaveSignatureTask(
                generateEnclaveSigningMaterialTask,
                generateEnclaveConfigTask,
                linuxExec
            )

            val generateEnclaveMetadataTask = taskHelper.generateEnclaveMetadataTask(
                signEnclaveWithKeyTask,
                addEnclaveSignatureTask,
                linuxExec
            )

            val buildSignedEnclaveTask = taskHelper.buildSignedEnclaveTask(generateEnclaveMetadataTask)

            val signedEnclaveJarTask =
                taskHelper.signedEnclaveJarTask(enclaveClassNameTask, buildSignedEnclaveTask)

            target.afterEvaluate {
                val runtimeType = conclaveExtension.runtime.get()

                target.logger.info("afterEvaluate Runtime Type: $runtimeType")

                when (runtimeType) {
                    RuntimeType.Gramine -> {
                        target.artifacts.add(taskHelper.typeLowerCase, buildGramineEnclaveJarTask.get().archiveFile)
                    }

                    RuntimeType.Graal -> {
                        target.artifacts.add(taskHelper.typeLowerCase, signedEnclaveJarTask.get().archiveFile)
                    }
                }
            }
        }
    }

    inner class TaskHelper(
        private val type: BuildType,
        private val conclaveExtension: ConclaveExtension,
        buildDirectory: Directory
    ) {
        val typeLowerCase: String = type.name.lowercase()

        private val enclaveExtension = getExtensionFromType(type, conclaveExtension)
        private val keyType = getSigningTypeFromType(type)
        private val enclaveDirectory: Path = baseDirectory.resolve(typeLowerCase)
        private val enclaveSignedSharedObject: Path = enclaveDirectory.resolve("enclave.signed.so")
        private val graalUnsignedEnclaveFile = enclaveDirectory.resolve("enclave.so")
        private val gramineBuildDir = baseDirectory.resolve("gramine").toString()

        init {
            enclaveExtension.signingType.set(keyType)

            // Set the default signing material location as an absolute path because if the
            // user overrides it they will use a project relative (rather than build directory
            // relative) path name.
            enclaveExtension.signingMaterial.set(buildDirectory.file("enclave/$type/signing_material.bin"))
        }

        fun buildUnsignedGraalEnclaveTask(
            shadowJarTask: ShadowJar,
            generateReflectionConfigTask: GenerateReflectionConfig,
            generateAppResourcesConfigTask: GenerateAppResourcesConfig,
            copyGraalVM: Exec,
            linuxExec: LinuxExec
        ): NativeImage {
            val linkerScriptFile = baseDirectory.resolve("Enclave.lds")

            return target.createTask(
                "buildUnsignedGraalEnclave$type",
                this@GradleEnclavePlugin,
                type,
                linkerScriptFile,
                linuxExec
            ) { task ->
                task.dependsOn(
                    copyGraalVM,
                    generateReflectionConfigTask,
                    generateAppResourcesConfigTask
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
                task.outputEnclave.set(graalUnsignedEnclaveFile.toFile())
            }
        }

        fun buildUnsignedEnclaveTask(buildUnsignedGraalEnclaveTask: NativeImage): BuildUnsignedEnclave {
            return target.createTask("buildUnsignedEnclave$type") { task ->
                task.inputEnclave.set(buildUnsignedGraalEnclaveTask.outputEnclave)
                task.outputEnclave.set(task.inputEnclave)
            }
        }

        fun generateEnclaveConfigTask(): GenerateEnclaveConfig {
            return target.createTask("generateEnclaveConfig$type", type) { task ->
                task.productID.set(conclaveExtension.productID)
                task.revocationLevel.set(conclaveExtension.revocationLevel)
                task.maxHeapSize.set(conclaveExtension.maxHeapSize)
                task.maxStackSize.set(conclaveExtension.maxStackSize)
                task.tcsNum.set(conclaveExtension.maxThreads)
                task.outputConfigFile.set(enclaveDirectory.resolve("enclave.xml").toFile())
            }
        }

        fun signEnclaveWithKeyTask(
            buildUnsignedEnclaveTask: BuildUnsignedEnclave,
            generateEnclaveConfigTask: GenerateEnclaveConfig,
            createDummyKeyTask: GenerateDummyMrsignerKey,
            linuxExec: LinuxExec
        ): SignEnclave {
            return target.createTask("signEnclaveWithKey$type", this@GradleEnclavePlugin, enclaveExtension, type, linuxExec) { task ->
                task.inputEnclave.set(buildUnsignedEnclaveTask.outputEnclave)
                task.inputEnclaveConfig.set(generateEnclaveConfigTask.outputConfigFile)
                task.inputKey.set(enclaveExtension.signingType.flatMap {
                    when (it) {
                        SigningType.DummyKey -> createDummyKeyTask.outputKey
                        SigningType.PrivateKey -> enclaveExtension.signingKey
                        else -> target.provider { null }
                    }
                })
                task.outputSignedEnclave.set(enclaveSignedSharedObject.toFile())
            }
        }

        fun generateEnclaveSigningMaterialTask(
            buildUnsignedEnclaveTask: BuildUnsignedEnclave,
            generateEnclaveConfigTask: GenerateEnclaveConfig,
            linuxExec: LinuxExec
        ): GenerateEnclaveSigningMaterial {
            return target.createTask("generateEnclaveSigningMaterial$type", this@GradleEnclavePlugin, linuxExec) { task ->
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
        }


        fun addEnclaveSignatureTask(
            generateEnclaveSigningMaterialTask: GenerateEnclaveSigningMaterial,
            generateEnclaveConfigTask: GenerateEnclaveConfig,
            linuxExec: LinuxExec
        ): AddEnclaveSignature {
            return target.createTask("addEnclaveSignature$type", this@GradleEnclavePlugin, linuxExec) { task ->
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
                task.outputSignedEnclave.set(enclaveSignedSharedObject.toFile())
            }
        }

        fun generateEnclaveMetadataTask(
            signEnclaveWithKeyTask: SignEnclave,
            addEnclaveSignatureTask: AddEnclaveSignature,
            linuxExec: LinuxExec
        ): GenerateEnclaveMetadata {
            return target.createTask("generateEnclaveMetadata$type", this@GradleEnclavePlugin, type, linuxExec) { task ->
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
        }

        fun buildSignedEnclaveTask(generateEnclaveMetadataTask: GenerateEnclaveMetadata): BuildSignedEnclave {
            return target.createTask("buildSignedEnclave$type") { task ->
                task.dependsOn(generateEnclaveMetadataTask)
                task.inputs.files(generateEnclaveMetadataTask.inputSignedEnclave)
                task.outputSignedEnclave.set(generateEnclaveMetadataTask.inputSignedEnclave)
            }
        }

        fun signedEnclaveJarTask(
            enclaveClassNameTask: EnclaveClassName,
            buildSignedEnclaveTask: BuildSignedEnclave
        ): TaskProvider<Jar> {
            return target.tasks.register("signedEnclave${type}Jar", Jar::class.java) { task ->
                task.group = CONCLAVE_GROUP
                task.description = "Compile an ${type}-mode enclave that can be loaded by SGX."
                task.dependsOn(enclaveClassNameTask)
                task.archiveAppendix.set("signed-so")
                task.archiveClassifier.set(typeLowerCase)
                // buildSignedEnclaveTask determines which of the three Conclave supported signing methods
                // to use to sign the enclave and invokes the correct task accordingly.
                task.from(buildSignedEnclaveTask.outputSignedEnclave)
                task.doFirst(RenameGraalTask(enclaveClassNameTask))
            }
        }

        fun buildUnsignedGramineEnclaveTask(shadowJarTask: ShadowJar): BuildUnsignedGramineEnclave {
            return target.createTask("buildUnsignedGramineEnclave$type") { task ->
                //  Even though the shadowJarTask is not used yet, it is required to trigger other
                //    dependencies, such as enclaveClassNameTask
                //  TODO: Once we use Java code instead of the bash example, we should remove the above comment
                task.dependsOn(shadowJarTask)
                task.outputs.dir(gramineBuildDir)
                task.buildDirectory.set(gramineBuildDir)
                task.archLibDirectory.set("/lib/x86_64-linux-gnu")
                task.entryPoint.set("/usr/local/lib/x86_64-linux-gnu/gramine/libsysdb.so")
                task.logType.set(GramineLogType.ERROR)
                task.outputManifest.set(
                    Paths.get(gramineBuildDir).resolve(BuildUnsignedGramineEnclave.MANIFEST_DIRECT).toFile()
                )
            }
        }

        fun buildGramineEnclaveJar(
            enclaveClassNameTask: EnclaveClassName,
            shadowJarTask: ShadowJar,
            buildUnsignedGramineEnclaveTask: BuildUnsignedGramineEnclave
        ) : TaskProvider<Jar> {
            val task = target.tasks.register("buildGramineEnclaveJar${type}", Jar::class.java) { task ->
                task.group = CONCLAVE_GROUP
                task.description = "Compile an ${type}-mode enclave that can be loaded by SGX."
                task.archiveFileName.set("enclave-gramine-$typeLowerCase.jar")
                task.archiveAppendix.set("jar")
                task.archiveClassifier.set(typeLowerCase)
                task.dependsOn(enclaveClassNameTask, shadowJarTask, buildUnsignedGramineEnclaveTask)

                task.from(shadowJarTask.archiveFile, buildUnsignedGramineEnclaveTask.outputManifest)
                task.doFirst(IntoGramineTask(enclaveClassNameTask))
            }
            return task
        }

        private fun getExtensionFromType(type: BuildType, conclaveExtension: ConclaveExtension): EnclaveExtension {
            return when (type) {
                BuildType.Release -> conclaveExtension.release
                BuildType.Debug -> conclaveExtension.debug
                BuildType.Simulation -> conclaveExtension.simulation
                else -> throw IllegalStateException()
            }
        }

        private fun getSigningTypeFromType(type: BuildType): SigningType {
            return when (type) {
                BuildType.Release -> SigningType.ExternalKey
                else -> SigningType.DummyKey
            }
        }

        inner class RenameGraalTask(private val enclaveClassNameTask: EnclaveClassName) : Action<Task> {
            override fun execute(task: Task) {
                val enclaveClassName = enclaveClassNameTask.outputEnclaveClassName.get()
                val location = enclaveClassName.substringBeforeLast('.').replace('.', '/')
                val renameTo = "${enclaveClassName.substringAfterLast('.')}-$typeLowerCase.signed.so"

                val jarTask = task as Jar
                jarTask.into(location)
                jarTask.rename { renameTo }
            }
        }

        inner class IntoGramineTask(private val enclaveClassNameTask: EnclaveClassName) : Action<Task> {
            override fun execute(task: Task) {
                //  Note that in Gramine we use the class name as a folder,
                //     not as a part of a file, as it is done with Native tasks
                val location = enclaveClassNameTask.outputEnclaveClassName.get().replace('.', '/') + "-${typeLowerCase}"
                println("location $location")
                val jarTask = task as Jar
                jarTask.into(location)
            }
        }
    }

    fun generateDummyMrsignerKey(): GenerateDummyMrsignerKey {
        return target.createTask("createDummyKey") { task ->
            task.outputKey.set(baseDirectory.resolve("dummy_key.pem").toFile())
        }
    }

    private fun generateReflectionConfigTask(enclaveClassNameTask: EnclaveClassName): GenerateReflectionConfig {
        return target.createTask("generateReflectionConfig") { task ->
            task.dependsOn(enclaveClassNameTask)
            task.enclaveClass.set(enclaveClassNameTask.outputEnclaveClassName)
            task.reflectionConfig.set(baseDirectory.resolve("reflectconfig").toFile())
        }
    }

    private fun generateAppResourcesConfigTask(shadowJarTask: ShadowJar): GenerateAppResourcesConfig {

        return target.createTask("generateAppResourcesConfig") { task ->
            task.dependsOn(shadowJarTask)
            task.jarFile.set(shadowJarTask.archiveFile)
            task.appResourcesConfigFile.set((baseDirectory / "app-resources-config.json").toFile())
        }
    }

    private fun linuxExec(sdkVersion: String): LinuxExec {
        return target.createTask("setupLinuxExecEnvironment") { task ->
            task.baseDirectory.set(target.projectDir.toPath().toString())
            task.tag.set("conclave-build:$sdkVersion")
            // Create a 'latest' tag too so users can follow our tutorial documentation using the
            // tag 'conclave-build:latest' rather than looking up the conclave version.
            task.tagLatest.set("conclave-build:latest")
        }
    }

    private fun copyGraalVM(): Exec {
        return target.createTask("copyGraalVM") { task ->
            task.outputs.dir(graalVMDistributionPath)

            // Create a configuration for downloading graalvm-*.tar.gz using Gradle
            val graalVMConfigName = "${task.name}Config"
            val configuration = target.configurations.create(graalVMConfigName)
            target.dependencies.add(graalVMConfigName, "com.r3.conclave:graalvm:$CONCLAVE_GRAALVM_VERSION@tar.gz")
            task.dependsOn(configuration)

            // Uncompress the graalvm-*.tar.gz
            Files.createDirectories(Paths.get(graalVMDistributionPath))
            task.workingDir(graalVMDistributionPath)
            task.commandLine("tar", "xf", LazyGraalVmFile(target, graalVMConfigName))
        }
    }


    // This is a hack to delay the execution of the code inside toString.
    // Gradle has three stages, initialization, configuration, and execution.
    // The code inside the toString function must run during the execution stage. For that to happen,
    // the following wrapper was created
    private class LazyGraalVmFile(val target: Project, val graalVMConfigName: String) {
        val graalVMAbsolutePath: String by lazy {
            target.configurations.findByName(graalVMConfigName)!!.files.single() {
                it.name.endsWith(
                    "tar.gz"
                )
            }.absolutePath
        }

        override fun toString(): String {
            return graalVMAbsolutePath
        }
    }

    private fun autoconfigureDependencyVersions(sdkVersion: String) {
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

    private fun checkGradleVersionCompatibility() {
        val gradleVersion = target.gradle.gradleVersion
        if (VersionNumber.parse(gradleVersion).baseVersion < VersionNumber(5, 6, 4, null)) {
            throw GradleException(
                "Project ${target.name} is using Gradle version $gradleVersion but the Conclave " +
                        "plugin requires at least version 5.6.4."
            )
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

inline fun <reified T : Task> Project.createTask(
    name: String,
    vararg constructorArgs: Any?,
    configure: (T) -> Unit
): T {
    val task = tasks.create(name, T::class.java, *constructorArgs)
    configure(task)
    return task
}
