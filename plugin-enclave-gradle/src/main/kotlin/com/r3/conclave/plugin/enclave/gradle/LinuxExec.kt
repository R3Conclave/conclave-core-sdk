package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.plugin.enclave.gradle.GradleEnclavePlugin.Companion.getManifestAttribute
import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject
import kotlin.io.path.*

open class LinuxExec @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        private val JEP_VERSION = getManifestAttribute("Jep-Version")
    }

    @get:Input
    val baseDirectory: Property<String> = objects.property(String::class.java)

    @get:Input
    val tag: Property<String> = objects.property(String::class.java)

    @get:Input
    val buildInDocker: Property<Boolean> = objects.property(Boolean::class.java)

    @get:Input
    val useInternalDockerRegistry: Property<Boolean> = objects.property(Boolean::class.java)

    override fun action() {
        // This task should be set as a dependency of any task that requires executing a command in the context
        // of a Linux system or container.
        // Building the enclave requires docker container to make the experience consistent between all OSs.
        // This helps with using Gramine too, as it's included in the docker container and users don't need to
        // installed it by themselves. Only Python Gramine enclaves are built outside the container.
        if (buildInDocker(buildInDocker) && !useInternalDockerRegistry.get()) {
            val conclaveBuildDir = temporaryDir.toPath() / "conclave-build"
            LinuxExec::class.java.copyResource("/conclave-build/Dockerfile", conclaveBuildDir / "Dockerfile")

            runDockerCommand(
                "docker",
                "build",
                "--tag", tag.get(),
                "--build-arg",
                "jep_version=$JEP_VERSION",
                conclaveBuildDir
            )

        }
    }

    private fun runDockerCommand(vararg dockerCommand: Any, commandLineConfig: CommandLineConfig = CommandLineConfig()): ExecResult {
        try {
            return commandLine(dockerCommand, commandLineConfig)
        } catch (e: Exception) {
            val message = if (OperatingSystem.current().isLinux) {
                "Conclave requires Docker to be installed when building enclaves. Please install Docker and " +
                        "rerun your build. See https://docs.conclave.net/enclave-modes.html#system-requirements " +
                        "for more information. If the build still fails, please rerun the build with the " +
                        "--stacktrace flag and raise an issue at https://github.com/R3Conclave/conclave-core-sdk/issues/new"
            } else {
                "Conclave requires Docker to be installed when building enclaves on non-Linux platforms. Please " +
                        "install Docker and rerun your build. See " +
                        "https://docs.conclave.net/running-hello-world.html#prerequisites and " +
                        "https://docs.conclave.net/writing-hello-world.html#configure-the-enclave-module for " +
                        "more information."
            }
            throw GradleException(message, e)
        }
    }

    /**
     * Non-Linux environments must always use Docker.
     */
    // We pass in the [buildInDocker] as a parameter, even though this task also has the same property, to make sure
    // the caller task re-runs if the buildInDocker config changes.
    // TODO Come up with a better way than this. This might not be an issue after CON-1069 since we won't be building
    //  the conclave-build image anymore.
    fun buildInDocker(buildInDocker: Property<Boolean>): Boolean {
        return !OperatingSystem.current().isLinux || buildInDocker.get()
    }

    /**
     * Prepare a file for use by a Docker invocation by copying it into a temporary directory
     * that lives in the project folder. The temporary directory and all files contained within
     * are deleted when cleanPreparedFiles() is called.
     */
    fun prepareFile(file: Path): Path {
        // Use the file as is if we're not using Docker
        if (!buildInDocker(buildInDocker)) return file

        val tmp = Paths.get(baseDirectory.get(), ".linuxexec").createDirectories()
        val newFile = Files.createTempFile(tmp, file.nameWithoutExtension, file.extension)
        // The source file may not exist if this is an output file. Let the actual command being
        // invoked handle any problems with missing/incorrect files
        if (file.exists()) {
            file.copyTo(newFile, REPLACE_EXISTING)
        }
        return newFile
    }

    /**
     * Remove any temporary files created by the invocation, including all files prepared
     * by a call to prepareFile().
     */
    fun cleanPreparedFiles() {
        if (buildInDocker(buildInDocker)) {
            this.project.delete(File("${baseDirectory.get()}/.linuxexec"))
        }
    }

    /** Returns the ERROR output of the command only, in the returned list. */
    fun exec(params: List<String>): List<String>? {
        val errorOut = ByteArrayOutputStream()

        val dockerCommand = if (buildInDocker(buildInDocker)) getDockerRunArgs(params) else params
        val commandLineConfig = CommandLineConfig(ignoreExitValue = true, errorOutputStream = errorOut)
        val result = runDockerCommand(dockerCommand, commandLineConfig)

        if (result.exitValue == 137) {
            // 137 = 128 + SIGKILL, which happens when the kernel out-of-memory killer runs.
            throwOutOfMemoryException()
        }
        if (result.exitValue != 0) {
            errorOut.writeTo(System.err)
            // Using default charset here because the strings come from a sub-process and that's what they'll pick up.
            // Hopefully it's UTF-8 - it should be!
            return String(errorOut.toByteArray()).split(System.lineSeparator())
        }
        result.assertNormalExitValue()
        return null
    }

    fun throwOutOfMemoryException(): Nothing = throw GradleException(
        "The sub-process ran out of RAM. On macOS or Windows, open the Docker preferences and " +
                "alter the amount of memory granted to the underlying virtual machine. We recommend at least 6 gigabytes of RAM " +
                "as the native image build process is memory intensive."
    )

    private fun getDockerRunArgs(params: List<String>): List<String> {
        // The first param is the name of the executable to run. We execute the command in the context of a VM (currently Docker) by
        // mounting the Host project directory as /project in the VM. We need to fix-up any path in parameters that point
        // to the project directory and convert them to point to /project instead, converting backslashes into forward slashes
        // to support Windows.
        val userId = commandWithOutput("id", "-u")
        val groupId = commandWithOutput("id", "-g")
        return listOf(
            "docker",
            "run",
            "-i",
            "--rm",
            "-u", "$userId:$groupId",
            "-v",
            "${baseDirectory.get()}:/project",
            tag.get()
        ) + params.map { it.replace(baseDirectory.get(), "/project").replace("\\", "/") }
    }
}
