@file:JvmName("SgxHost")
package com.r3.sgx.plugin.host

import com.bmuschko.gradle.docker.DockerExtension
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer.ExposedPort
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.r3.sgx.plugin.BuildType
import com.r3.sgx.plugin.SGX_GROUP
import com.r3.sgx.plugin.enclave.BuildSignedEnclave
import com.r3.sgx.plugin.enclave.GetEnclaveClassName
import com.r3.sgx.plugin.enclave.SgxEnclavePlugin
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.*
import java.nio.file.Paths
import java.time.Duration.ofNanos
import java.time.Duration.ofSeconds
import java.util.jar.JarFile.MANIFEST_NAME
import java.util.jar.Manifest
import javax.inject.Inject

private const val DEFAULT_BASE_IMAGE_NAME = "com.r3.sgx/enclavelet-host"
private const val DEFAULT_TEST_GRPC_PORT = 30080
private const val DEFAULT_TEST_START_TIMEOUT = 30
private const val SGX_DEVICE_NODE = "/dev/isgx"
const val DEFAULT_TAG = "latest"
const val GRPC_PORT = 8080

inline fun <reified T> ObjectFactory.emptySetOf(): SetProperty<T> = setProperty(T::class.java).empty()
inline fun <reified T> ObjectFactory.emptyListOf(): ListProperty<T> = listProperty(T::class.java).empty()
inline fun <reified K, reified V> ObjectFactory.emptyMapOf(): MapProperty<K, V> = mapProperty(K::class.java, V::class.java).empty()

/**
 * Conclave metadata object that is populated from the plugin's MANIFEST.MF.
 */
private data class ArtifactMetadata(
    val dockerRegistry: String,
    val version: String
)

private val CONCLAVE_METADATA= readProductMetadataFromManifest()

private fun readProductMetadataFromManifest(): ArtifactMetadata {
    val classLoader = SgxHostPlugin::class.java.classLoader
    val manifestUrls = classLoader.getResources(MANIFEST_NAME).toList()
    for (manifestUrl in manifestUrls) {
        return manifestUrl.openStream().use(::getArtifactMetadataFrom) ?: continue
    }
    throw IllegalStateException("Could not find Conclave tags in plugin's manifest")
}

private fun getArtifactMetadataFrom(manifestStream: InputStream): ArtifactMetadata? {
    val manifest = Manifest(manifestStream)
    val registry = manifest.mainAttributes.getValue("Conclave-Docker-Registry") ?: return null
    val version = manifest.mainAttributes.getValue("Conclave-Version") ?: return null
    return ArtifactMetadata(
        dockerRegistry = registry,
        version = version
    )
}

/**
 * Gradle plugin for baking enclave objects into a base Docker image
 * that contains the enclavelet host code.
 */
class SgxHostPlugin @Inject constructor(private val factory: ObjectFactory): Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            logger.info("Applying the Docker plugin")
            pluginManager.apply(DockerRemoteApiPlugin::class.java)

            logger.info("Applying the SGX Enclave plugin")
            pluginManager.apply(SgxEnclavePlugin::class.java)

            logger.lifecycle("Base Docker Registry: {}", CONCLAVE_METADATA.dockerRegistry)
            logger.lifecycle("SGX-JVM Version: {}", CONCLAVE_METADATA.version)

            val docker = extensions.getByName("docker") as DockerExtension
            val registryCredentials = (docker as ExtensionAware).extensions.getByName("registryCredentials") as DockerRegistryCredentials
            val baseCredentials = target.extensions.create("baseRegistry", DockerRegistryCredentials::class.java, project.objects).apply {
                url.set(CONCLAVE_METADATA.dockerRegistry)
                username.set(registryCredentials.username)
                password.set(registryCredentials.password)
            }

            for (buildType in BuildType.values()) {
                logger.info("Creating tasks for $buildType Enclavelet Host")
                factory.newInstance(EnclaveImage::class.java, target, buildType, registryCredentials, baseCredentials).createTasks()
            }
        }
    }
}

private open class EnclaveImage @Inject constructor(
    private val objects: ObjectFactory,
    layout: ProjectLayout,
    private val target: Project,
    private val buildType: BuildType,
    private val registryCredentials: DockerRegistryCredentials,
    private val baseCredentials: DockerRegistryCredentials
) {
    private companion object {
        private const val WAIT_MILLIS = 100L
        private const val ONE_SECOND = 1000
        private const val CONFIGURATION_FILE = "/app/config/config.yml"
        private val EXPOSED_PORTS = listOf(ExposedPort("tcp", listOf(GRPC_PORT)))
        private val AESMD: File = Paths.get("/run", "aesmd").toFile()
        private inline val ANY_ADDRESS: InetAddress? get() = null
    }

    private val imageExtension = target.extensions.create("enclaveImage$buildType",
        EnclaveImageExtension::class.java,
        registryCredentials,
        DEFAULT_BASE_IMAGE_NAME,
        CONCLAVE_METADATA.version,
        target.provider { "${target.group}/${target.name}" }, // group is configurable, so read it lazily
        CONCLAVE_METADATA.version,
        DEFAULT_TEST_GRPC_PORT,
        DEFAULT_TEST_START_TIMEOUT
    )
    private val buildTypeTag: String = buildType.name.toLowerCase()
    private val publishableNameWithType = imageExtension.publishableName.map { name -> "$name-$buildTypeTag" }
    private val buildImageOutputDir = layout.buildDirectory.dir("docker-$buildTypeTag-build")
    private val commandOptions = emptyList<String>()

    init {
        target.tasks.withType(BuildSignedEnclave::class.java)
            .singleOrNull { task -> task.name.endsWith(buildType.toString()) }
            ?.also { task ->
                target.logger.info("$buildType image will be built using ${task.path}")
                imageExtension.enclaveObject.set(task.outputSignedEnclave)
            }
    }

    fun createTasks() {
        val prepareImageTask = createPrepareImageTask()
        val buildImageTask = createBuildImageTaskFor(prepareImageTask.dockerDir)
        createPushImageTasksFor(buildImageTask)
        createContainerTasksFor(buildImageTask)
    }

    /**
     * Create a directory containing a Dockerfile along with everything that
     * Dockerfile needs. This is the input for [DockerBuildImage].
     */
    private fun createPrepareImageTask(): PrepareEnclaveImage = with(target) {
        return tasks.create("prepareEnclaveImage$buildType", PrepareEnclaveImage::class.java) { task ->
            val enclaveClassNameTask = target.tasks.withType(GetEnclaveClassName::class.java).single()
            task.dependsOn(enclaveClassNameTask)
            task.dockerDir.set(buildDir.resolve("docker-$buildTypeTag"))
            task.repositoryUrl.set(CONCLAVE_METADATA.dockerRegistry)
            task.baseImageName.set(imageExtension.baseImageName)
            task.enclaveObject.set(imageExtension.enclaveObject)
            task.enclaveClassName.set(enclaveClassNameTask.outputEnclaveClassName)
            task.tag.set(imageExtension.baseTag)

            task.commandOptions.set(commandOptions)
            task.hostOptions.set(listOf("--enclave-load-mode", buildType.name.toUpperCase()))
        }
    }

    /**
     * Build a Docker image containing our enclavelet. The output directory is
     * created lazily and contains the image's ID.
     */
    private fun createBuildImageTaskFor(dockerDir: DirectoryProperty): TaskProvider<DockerBuildImage> = with(target) {
        return tasks.register("buildEnclaveImage$buildType", DockerBuildImage::class.java) { task ->
            task.group = SGX_GROUP
            task.registryCredentials = baseCredentials
            task.inputDir.set(dockerDir)
            task.tags.set(imageExtension.fullImageNamesFor(publishableNameWithType))

            val dummyOutput = buildImageOutputDir.map { it.file("dummy-build") }
            task.outputs.file(dummyOutput)
            task.doLast {
                dummyOutput.get().asFile.writeText(task.imageId.get() + System.lineSeparator())
            }
        }
    }

    /**
     * Push the Docker image into the repository with required tags.
     */
    private fun createPushImageTasksFor(buildImageTask: TaskProvider<*>): Unit = with(target) {
        /**
         * Always push the Docker image with the latest tag.
         */
        val pushLatestTask = tasks.register("pushEnclaveImage${buildType}AsLatest", DockerPushImage::class.java) { task ->
            task.group = SGX_GROUP
            task.registryCredentials = registryCredentials
            task.imageName.set(publishableNameWithType)
            task.tag.set(DEFAULT_TAG)

            // Ensure that we notice when the build image ID has been updated.
            task.dependsOn(buildImageTask)
            task.inputs.dir(buildImageOutputDir)

            val dummyOutput = buildDir.resolve("dummy-$buildTypeTag-push-latest" )
            task.outputs.file(dummyOutput)
            task.doLast {
                dummyOutput.writeText(task.imageName.get() + System.lineSeparator())
            }
        }

        /**
         * Optionally push the Docker image with a user-supplied tag.
         */
        val pushTagTask = tasks.register("pushEnclaveImage${buildType}Tag", DockerPushImage::class.java) { task ->
            task.group = SGX_GROUP
            task.registryCredentials = registryCredentials
            task.imageName.set(publishableNameWithType)
            task.tag.set(imageExtension.publishTag)

            // Ensure that we notice when the build image ID has been updated.
            task.dependsOn(buildImageTask)
            task.inputs.dir(buildImageOutputDir)

            val dummyOutput = buildDir.resolve("dummy-$buildTypeTag-push-tag" )
            task.outputs.file(dummyOutput)
            task.doLast {
                dummyOutput.writeText(task.imageName.get() + '+' + task.tag.get() + System.lineSeparator())
            }
            task.onlyIf {
                task.tag.isPresent
            }
        }

        /**
         * Dummy task to wrap the two actual "push" tasks.
         */
        tasks.register("pushEnclaveImage$buildType") { task ->
            task.dependsOn(pushLatestTask, pushTagTask)
            task.group = SGX_GROUP
        }
    }

    /**
     * Create tasks for testing this Docker image inside an ephemeral container.
     */
    private fun createContainerTasksFor(buildImageTask: TaskProvider<*>): Unit = with(target) {
        val createContainerTask = tasks.create("createEnclaveContainer$buildType", DockerCreateContainer::class.java) { task ->
            task.group = SGX_GROUP
            task.tty.set(true)
            task.autoRemove.set(imageExtension.testing.removeOnExit)
            task.imageId.set(publishableNameWithType)
            task.exposedPorts.set(EXPOSED_PORTS)
            task.portBindings.set(imageExtension.testing.portBindings)
            task.binds.set(getMountBindingsFor(imageExtension.testing))
            task.cmd.set(getHostOptionsFor(imageExtension.testing))

            if (buildType != BuildType.Simulation) {
                // This is required for hardware enclaves.
                task.devices.set(listOf(SGX_DEVICE_NODE))
            }

            // Ensure that we notice when the build image ID has been updated.
            task.dependsOn(buildImageTask)
            task.inputs.dir(buildImageOutputDir)
        }

        val stopContainerTask = tasks.register("stopEnclaveContainer$buildType", DockerStopContainer::class.java) { task ->
            task.group = SGX_GROUP
            task.containerId.set(createContainerTask.containerId)

            // The container may already have exited or even never started, so allow this task to fail quietly.
            task.onError { ex ->
                logger.warn("Failed to stop '{}' container: {}", buildType, ex.message)
            }
        }

        tasks.register("startEnclaveContainer$buildType", DockerStartContainer::class.java) { task ->
            task.group = SGX_GROUP
            task.containerId.set(createContainerTask.containerId)
            task.dependsOn(createContainerTask)
            task.finalizedBy(stopContainerTask)
            task.doLast {
                val hostAddress = InetSocketAddress(ANY_ADDRESS, imageExtension.testing.grpcPort.get())
                val timeoutSeconds = imageExtension.testing.startTimeout.getOrElse(DEFAULT_TEST_START_TIMEOUT)
                if (!pollForStartUp(hostAddress, timeoutSeconds)) {
                    throw InvalidUserCodeException("Container has not started within $timeoutSeconds seconds.")
                }
            }
        }
    }

    private fun getHostOptionsFor(container: EnclaveContainer): Provider<List<String>> {
        return objects.listProperty(String::class.java).flatMap {
            objects.emptyListOf<String>().also { list ->
                list.addAll("--port", GRPC_PORT.toString())
                if (container.configFile.isPresent) {
                    list.addAll("--config", CONFIGURATION_FILE)
                }
            }
        }
    }

    private fun getMountBindingsFor(container: EnclaveContainer): Provider<Map<String, String>> {
        return objects.mapProperty(String::class.java, String::class.java).flatMap {
            objects.emptyMapOf<String, String>().also { map ->
                if (container.configFile.isPresent) {
                    map.putAll(container.configFile.map { f ->
                        val file = f.asFile
                        if (!file.isFile) {
                            throw InvalidUserDataException("No such file '${file.absolutePath}'")
                        }
                        mapOf(file.absolutePath to CONFIGURATION_FILE)
                    })
                }

                if (buildType != BuildType.Simulation && AESMD.isDirectory) {
                    // This is required for hardware enclaves.
                    map.put(AESMD.absolutePath, AESMD.absolutePath)
                }
            }
        }
    }

    private fun pollForStartUp(address: SocketAddress, timeout: Int): Boolean {
        val startTime = System.nanoTime()
        val endTime = startTime + ofSeconds(timeout.toLong()).toNanos()
        while (System.nanoTime() < endTime) {
            val socket = Socket()
            socket.reuseAddress = true
            try {
                socket.connect(address, ONE_SECOND)
                socket.close()
            } catch (e: ConnectException) {
                Thread.sleep(WAIT_MILLIS)
                continue
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: IOException) {
                throw InvalidUserCodeException(e.message ?: "", e)
            }

            // Success! Display start-up time to nearest 0.1 seconds.
            val interval = ofNanos(System.nanoTime() - startTime).toMillis() + 50
            target.logger.lifecycle("Host started after {} seconds.",
                     String.format("%d.%01d", (interval / ONE_SECOND), (interval % ONE_SECOND) / 100))
            return true
        }

        // Time is up - Fail!
        return false
    }
}
