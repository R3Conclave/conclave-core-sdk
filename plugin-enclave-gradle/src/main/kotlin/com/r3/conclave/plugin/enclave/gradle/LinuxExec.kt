package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject

open class LinuxExec @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:Input
    val baseDirectory: Property<String> = objects.property(String::class.java)

    @get:Input
    val tag: Property<String> = objects.property(String::class.java)

    @get:Input
    val tagLatest: Property<String> = objects.property(String::class.java)

    override fun action() {
        // This task should be set as a dependency of any task that requires executing a command in the context
        // of a Linux system or container.
        // Building the enclave requires docker container to make the experience consistent between all OSs.
        // This helps with using Gramine too, as it's included in the docker container and users don't need to
        // installed it by themselves. Only Python Gramine enclaves are built outside the container.

        val conclaveBuildDir = temporaryDir.toPath() / "conclave-build"
        LinuxExec::class.java.copyResource("/conclave-build/Dockerfile", conclaveBuildDir / "Dockerfile")

        try {
            commandLine(
                "docker",
                "build",
                "--tag", tag.get(),
                "--tag", tagLatest.get(),
                conclaveBuildDir
            )
        } catch (e: Exception) {
            throw GradleException(
                "Conclave requires Docker to be installed when building GraalVM native-image based enclaves. "
                        + "Please install Docker and rerun your build. "
                        + "See https://docs.conclave.net/tutorial.html#setting-up-your-machine and "
                        + "https://docs.conclave.net/writing-hello-world.html#configure-the-enclave-module"
            )
        }

    }

    /**
     * Prepare a file for use by a Docker invocation by copying it into a temporary directory
     * that lives in the project folder. The temporary directory and all files contained within
     * are deleted when cleanPreparedFiles() is called.
     */
    fun prepareFile(file: File): File {
        val tmp = File("${baseDirectory.get()}/.linuxexec")
        tmp.mkdir()
        val newFile = File.createTempFile(file.nameWithoutExtension, file.extension, tmp)
        // The source file may not exist if this is an output file. Let the actual command being
        // invoked handle any problems with missing/incorrect files
        try {
            Files.copy(file.toPath(), newFile.toPath(), REPLACE_EXISTING)
        } catch (e: IOException) {
        }
        return newFile
    }

    /**
     * Remove any temporary files created by the invocation, including all files prepared
     * by a call to prepareFile().
     */
    fun cleanPreparedFiles() {
        this.project.delete(File("${baseDirectory.get()}/.linuxexec"))
    }

    /** Returns the ERROR output of the command only, in the returned list. */
    fun exec(params: List<String>): List<String>? {
        // The first param is the name of the executable to run. We execute the command in the context of a VM (currently Docker) by
        // mounting the Host project directory as /project in the VM. We need to fix-up any path in parameters that point
        // to the project directory and convert them to point to /project instead, converting backslashes into forward slashes
        // to support Windows.
        val args: List<String> = listOf(
            "docker",
            "run",
            "-i",
            "--rm",
            "-v",
            "${baseDirectory.get()}:/project",
            tag.get()
        ) + params.map { it.replace(baseDirectory.get(), "/project").replace("\\", "/") }

        val errorOut = ByteArrayOutputStream()
        val result = commandLine(
            args,
            commandLineConfig = CommandLineConfig(ignoreExitValue = true, errorOutputStream = errorOut)
        )

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

    fun execWithOutput(params: List<String>): String {
        val args: List<String> = listOf(
            "docker",
            "run",
            "-i",
            "--rm",
            "-v",
            "${baseDirectory.get()}:/project",
            tag.get()
        ) + params.map { it.replace(baseDirectory.get(), "/project").replace("\\", "/") }

        return commandWithOutput(*args.toTypedArray()).trimEnd()
    }

    fun throwOutOfMemoryException(): Nothing = throw GradleException(
        "The sub-process ran out of RAM. Open the Docker preferences and " +
                "alter the amount of memory granted to the underlying virtual machine. We recommend at least 6 gigabytes of RAM " +
                "as the native image build process is memory intensive."
    )
}
