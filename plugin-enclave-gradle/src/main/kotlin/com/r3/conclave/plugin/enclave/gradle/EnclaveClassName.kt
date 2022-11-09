package com.r3.conclave.plugin.enclave.gradle

import io.github.classgraph.ClassGraph
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class EnclaveClassName @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val sourceFatJar: RegularFileProperty = objects.fileProperty()

    // Ideally we wouldn't write the output of the scan into a file, since doesn't need to be read outside of the
    // plugin. However, Gradle tracks changes and up-to-date checks via files and directories, and so we write the
    // result of the scan to a file which can then tracked by depdendent task. Notice how an @Output annotation
    // doesn't exist.
    @get:OutputFile
    val enclaveClassNameFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        logger.info("Scanning for enclave class in ${sourceFatJar.get()}")
        val enclaveClassName = ClassGraph()
            .overrideClasspath(sourceFatJar.get())
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
        enclaveClassNameFile.asFile.get().writeText(enclaveClassName)
    }

    /**
     * Returns a [Provider] of the enclave class name. This provider will carry the dependency information of this task
     * to any second task which uses providers for input values. This means the dependency tree will be wired up
     * automatically and there's no need to call [org.gradle.api.Task.dependsOn].
     */
    fun enclaveClassName(): Provider<String> = enclaveClassNameFile.map { it.asFile.readText().trimEnd() }
}
