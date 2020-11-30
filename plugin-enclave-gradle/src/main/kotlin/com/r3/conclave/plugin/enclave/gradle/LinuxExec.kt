package com.r3.conclave.plugin.enclave.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.internal.os.OperatingSystem
import javax.inject.Inject

open class LinuxExec  @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val dockerFile: RegularFileProperty = objects.fileProperty()

    @get:Input
    val baseDirectory: Property<String> = objects.property(String::class.java)

    @get:Input
    val tag: Property<String> = objects.property(String::class.java)

    @get:Input
    val tagLatest: Property<String> = objects.property(String::class.java)

    override fun action() {
        // This task should be set as a dependency of any task that requires executing a command in the context
        // of a Linux system or container. The action checks to see if the Host OS is Linux and if not sets
        // up a Linux environment (currently using Docker) in which the commands will be executed.
        if (!OperatingSystem.current().isLinux) {
            try {
                project.exec { spec ->
                    spec.commandLine("docker",
                            "build",
                            "--tag", tag.get(),
                            "--tag", tagLatest.get(),
                            dockerFile.asFile.get().parentFile.absolutePath
                    )
                }
            }
            catch (e: Exception) {
                throw GradleException("Conclave requires Docker to be installed when building GraalVM native-image based enclaves on non-Linux platforms. "
                                    + "Try installing Docker or setting 'runtime = avian' in your enclave build.gradle file instead. "
                                    + "See https://docs.conclave.net/tutorial.html#setting-up-your-machine and "
                                    + "https://docs.conclave.net/writing-hello-world.html#configure-the-enclave-module")
            }
        }
    }

    fun exec(params: List<String>) {
        // If the host OS is Linux then we just execute the params that we are given. The first param is the name of the
        // executable to run. If the host OS is not Linux then we execute in the context of a VM (currently Docker) by
        // mounting the Host build directory as /project in the VM. We need to fix-up any path in parameters that point
        // to the build directory and convert them to point to /project instead, converting backslashes into forward slashes
        // to support Windows.
        val args: List<String> = when (OperatingSystem.current().isLinux) {
            true -> params
            false -> listOf(
                    "docker",
                    "run",
                    "-i",
                    "--rm",
                    "-v",
                    "${baseDirectory.get()}:/project",
                    tag.get()
            ) + params.map { it.replace(baseDirectory.get(), "/project").replace("\\", "/") }
        }
        val result = project.exec { spec ->
            spec.commandLine(args)
            spec.isIgnoreExitValue = true   // We'll handle it in a moment.
        }
        if (result.exitValue == 137) {
            // 137 = 128 + SIGKILL, which happens when the kernel out-of-memory killer runs.
            throw GradleException("The build process ran out of RAM. On macOS or Windows, open the Docker preferences and " +
                    "alter the amount of memory granted to the underlying virtual machine. We recommend at least 6 gigabytes of RAM " +
                    "as the native image build process is memory intensive.")
        }
        result.assertNormalExitValue()
    }
}
