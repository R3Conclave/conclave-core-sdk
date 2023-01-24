package com.r3.conclave.plugin.enclave.gradle

import io.github.classgraph.ClassGraph
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import javax.inject.Inject

open class EnclaveClassName @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:Input
    val inputClassPath: Property<FileCollection> = objects.property(FileCollection::class.java)

    private val _outputEnclaveClassName = objects.property(String::class.java)
    @get:Internal
    val outputEnclaveClassName: Provider<String> get() = _outputEnclaveClassName

    override fun action() {
        logger.info("Scanning for enclave class: ${inputClassPath.get().asPath}")
        val enclaveClassName = ClassGraph()
                .overrideClasspath(inputClassPath.get().asPath)
                .enableClassInfo()
                .scan()
                .use {
                    val enclaveClasses = it.getSubclasses("com.r3.conclave.enclave.Enclave").filter { !it.isAbstract }
                    when (enclaveClasses.size) {
                        0 -> throw GradleException("Could not detect a class that extends com.r3.conclave.enclave.Enclave. " +
                                "Please implement the enclave class and ensure that its visibility is public.")
                        1 -> enclaveClasses[0].name
                        else -> throw GradleException("There can only be one Enclave class in a Gradle module but multiple " +
                                "were found (${enclaveClasses.joinToString { it.name }}). See " +
                                "https://github.com/R3Conclave/conclave-core-sdk/wiki/FAQ#can-i-load-more-than-one-enclave-at-once for more information.")
                    }
                }
        _outputEnclaveClassName.set(enclaveClassName)
    }
}
