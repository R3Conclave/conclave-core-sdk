package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.internal.PluginUtils.DOCKER_WORKING_DIR
import com.r3.conclave.common.internal.PluginUtils.getManifestAttribute
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

open class LinuxExec @Inject constructor(objects: ObjectFactory, private val isPythonEnclave: Boolean) :
    ConclaveTask() {

    companion object {
        private val GRAMINE_VERSION = getManifestAttribute("Gramine-Version")
    }

    @get:Input
    val baseDirectory: Property<String> = objects.property(String::class.java)

    @get:Input
    val tag: Property<String> = objects.property(String::class.java)

    @get:Input
    val buildInDocker: Property<Boolean> = objects.property(Boolean::class.java)

    @get:Input
    val runtimeType: Property<GradleEnclavePlugin.RuntimeType> = objects.property(GradleEnclavePlugin.RuntimeType::class.java)

    override fun action() {
        // This task should be set as a dependency of any task that requires executing a command in the context
        // of a Linux system or container.
        // Building the enclave requires docker container to make the experience consistent between all OSs.
        // This helps with using Gramine too, as it's included in the docker container and users don't need to
        // installed it by themselves. Only Python Gramine enclaves are built outside the container.
        if (buildInDocker(buildInDocker)) {
            val conclaveBuildDir = temporaryDir.toPath() / "conclave-build"
            LinuxExec::class.java.copyResource("/conclave-build/Dockerfile", conclaveBuildDir / "Dockerfile")

            runDockerCommand(listOf(
                    "docker",
                    "build",
                    "--tag", tag.get(),
                    "--build-arg",
                    "gramine_version=$GRAMINE_VERSION",
                    conclaveBuildDir
                )
            )
        }
    }

    private fun runDockerCommand(dockerCommand: List<Any?>, commandLineConfig: CommandLineConfig = CommandLineConfig()): ExecResult {
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
        // Gramine enclaves are always built in a Docker container, apart from Python enclaves.
        // TODO: CON-1229 - Build Python Gramine enclaves inside the conclave-build container.
        // GraalVM enclaves are built in a Docker container by default, but the user can opted out by setting the buildInDocker config to "false"
        return !OperatingSystem.current().isLinux || (buildInDocker.get() && !isPythonEnclave) || (runtimeType.get() == GradleEnclavePlugin.RuntimeType.GRAMINE && !isPythonEnclave)
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

    /** Returns the standard output of the command or throws a Gradle exception in case of failures. */
    fun exec(command: List<String>, dockerExtraParams: List<String> = emptyList()): String {
        val errorOut = ByteArrayOutputStream()
        val stdOut = ByteArrayOutputStream()

        val result = runDockerCommand(
            dockerCommand = if (buildInDocker(buildInDocker)) getDockerRunArgs(command, dockerExtraParams) else command,
            commandLineConfig = CommandLineConfig(
                ignoreExitValue = true,
                standardOutputStream = stdOut,
                errorOutputStream = errorOut
            )
        )

        if (result.exitValue == 137) {
            // 137 = 128 + SIGKILL, which happens when the kernel out-of-memory killer runs.
            throwOutOfMemoryException()
        }
        if (result.exitValue != 0) {
            errorOut.writeTo(System.err)
            throw GradleException(errorOut.toString())
        }
        result.assertNormalExitValue()
        return stdOut.toString()
    }

    fun throwOutOfMemoryException(): Nothing = throw GradleException(
        "The sub-process ran out of RAM. On macOS or Windows, open the Docker preferences and " +
                "alter the amount of memory granted to the underlying virtual machine. We recommend at least 6 gigabytes of RAM " +
                "as the native image build process is memory intensive."
    )

    private fun List<String>.mapToDockerWorkingDirectory(): List<String> {
        return map {
            it.replace(baseDirectory.get(), DOCKER_WORKING_DIR).replace("\\", "/")
        }
    }

    private fun getDockerRunArgs(command: List<String>, extraParams: List<String>): List<String> {
        // The first param is the name of the executable to run. We execute the command in the context of a VM (currently Docker) by
        // mounting the Host project directory as /project in the VM. We need to fix-up any path in parameters that point
        // to the project directory and convert them to point to /project instead, converting backslashes into forward slashes
        // to support Windows.
        val userId = commandWithOutput("id", "-u")
        val groupId = commandWithOutput("id", "-g")
        val image = tag.get()
        val dockerizedCommand = command.mapToDockerWorkingDirectory()
        val dockerizedExtraParams = extraParams.mapToDockerWorkingDirectory()
        val dockerRun = listOf(
            "docker",
            "run",
            "--rm",
            "-u", "$userId:$groupId",
            "-v", "${baseDirectory.get()}:$DOCKER_WORKING_DIR"
        )
        return dockerRun + dockerizedExtraParams + image + dockerizedCommand
    }
}
