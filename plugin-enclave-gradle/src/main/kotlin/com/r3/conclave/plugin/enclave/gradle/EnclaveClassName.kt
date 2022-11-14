package com.r3.conclave.plugin.enclave.gradle

import io.github.classgraph.ClassGraph
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class EnclaveClassName @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFiles
    val classPath: ConfigurableFileCollection = objects.fileCollection()

    // Gradle uses inputs and outputs to determine if a task needs to run again (aka incremental builds). If a task
    // doesn't declare an output then it has no way of determining this and will report the warning:
    //    Task ':enclaveClassName' is not up-to-date because:
    //      Task has not declared any outputs despite executing actions.
    //
    // For this reason we must declare a file output, even though it's not necssary for this task, and read from it
    // when other tasks need the result.
    // https://docs.gradle.org/current/userguide/custom_tasks.html#incremental_tasks
    @get:OutputFile
    val enclaveClassNameFile: RegularFileProperty = objects.fileProperty()

    /**
     * Gradle makes sure the Provider returned by this task is linked to the output, and so setting this on another
     * task's input will automatically create the "depends on" relationship between the two tasks.
     */
    fun enclaveClassName(): Provider<String> = enclaveClassNameFile.map { it.asFile.readText().trimEnd() }

    override fun action() {
        logger.info("Scanning for enclave class: ${classPath.asPath}")
        val enclaveClassName = ClassGraph()
                .overrideClasspath(classPath.asPath)
                .enableClassInfo()
                .scan()
                .use { result ->
                    val enclaveClasses = result.getSubclasses("com.r3.conclave.enclave.Enclave").filter { !it.isAbstract }
                    when (enclaveClasses.size) {
                        0 -> throw GradleException("Could not detect a class that extends com.r3.conclave.enclave.Enclave. " +
                                "Please implement the enclave class and ensure that its visibility is public.")
                        1 -> enclaveClasses[0].name
                        else -> throw GradleException("There can only be one Enclave class in a Gradle module but multiple " +
                                "were found (${enclaveClasses.joinToString { it.name }}). See " +
                                "https://docs.conclave.net/faq.html#can-i-load-more-than-one-enclave-at-once for more information.")
                    }
                }
        enclaveClassNameFile.get().asFile.writeText(enclaveClassName)
    }
}
