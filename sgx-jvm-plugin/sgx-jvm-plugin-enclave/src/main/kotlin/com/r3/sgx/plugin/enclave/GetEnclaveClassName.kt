package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import io.github.classgraph.ClassGraph
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import javax.inject.Inject

open class GetEnclaveClassName @Inject constructor(objects: ObjectFactory) : SgxTask() {
    companion object {
        private const val OLD_ENCLAVE_CLASS_NAME = "com.r3.sgx.core.enclave.Enclave"
        private const val CONCLAVE_ENCLAVE_CLASS_NAME = "com.r3.conclave.enclave.Enclave"
    }

    @get:InputFile
    val inputJar: RegularFileProperty = objects.fileProperty()

    private val _outputEnclaveClassName = objects.property(String::class.java)
    val outputEnclaveClassName: Provider<String> get() = _outputEnclaveClassName

    override fun sgxAction() {
        logger.info("Scanning ${inputJar.get()}")
        val enclaveClassName = ClassGraph()
                .overrideClasspath(inputJar.get())
                .enableClassInfo()
                .scan()
                .use {
                    val enclaveClasses = (it.getSubclasses(CONCLAVE_ENCLAVE_CLASS_NAME) + it.getClassesImplementing(OLD_ENCLAVE_CLASS_NAME)).filter { !it.isAbstract }
                    when (enclaveClasses.size) {
                        0 -> throw GradleException("There are no classes that extend $CONCLAVE_ENCLAVE_CLASS_NAME")
                        1 -> enclaveClasses[0].name
                        else -> throw GradleException("Found multiple enclave classes: ${enclaveClasses.joinToString { it.name }}")
                    }
                }

        logger.lifecycle("Enclave class: $enclaveClassName")
        _outputEnclaveClassName.set(enclaveClassName)
    }
}
