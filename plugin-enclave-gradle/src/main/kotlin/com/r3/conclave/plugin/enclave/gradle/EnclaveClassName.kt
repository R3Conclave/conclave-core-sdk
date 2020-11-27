package com.r3.conclave.plugin.enclave.gradle

import io.github.classgraph.ClassGraph
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import javax.inject.Inject

open class EnclaveClassName @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    @get:InputFile
    val inputJar: RegularFileProperty = objects.fileProperty()

    private val _outputEnclaveClassName = objects.property(String::class.java)
    @get:Internal
    val outputEnclaveClassName: Provider<String> get() = _outputEnclaveClassName

    override fun action() {
        logger.info("Scanning ${inputJar.get()}")
        val enclaveClassName = ClassGraph()
                .overrideClasspath(inputJar.get())
                .enableClassInfo()
                .scan()
                .use {
                    val enclaveClasses = it.getSubclasses("com.r3.conclave.enclave.Enclave").filter { !it.isAbstract }
                    when (enclaveClasses.size) {
                        0 -> throw GradleException("There are no classes that extend com.r3.conclave.enclave.Enclave")
                        1 -> enclaveClasses[0].name
                        else -> throw GradleException("There can only be one Enclave class in a Gradle module but multiple " +
                                "were found (${enclaveClasses.joinToString { it.name }}). See " +
                                "https://docs.conclave.net/faq.html#can-i-load-more-than-one-enclave-at-once for more information.")
                    }
                }
        _outputEnclaveClassName.set(enclaveClassName)
    }
}
