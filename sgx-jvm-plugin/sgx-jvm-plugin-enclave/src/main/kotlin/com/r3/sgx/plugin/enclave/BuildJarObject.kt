package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
import com.r3.sgx.plugin.enclave.BuildJarObject.Companion.CONCLAVE_ENCLAVE_CLASS_NAME
import com.r3.sgx.plugin.enclave.BuildJarObject.Companion.OLD_ENCLAVE_CLASS_NAME
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarInputStream
import javax.inject.Inject

open class BuildJarObject @Inject constructor(objects: ObjectFactory) : SgxTask() {
    companion object {
        fun readEnclaveClassName(jar: URL): String {
            val manifest = JarInputStream(jar.openStream()).use { it.manifest }
            return manifest.mainAttributes.getValue(ENCLAVE_CLASS_ATTRIBUTE) ?:
                throw InvalidUserDataException("Attribute '$ENCLAVE_CLASS_ATTRIBUTE' missing from $jar")
        }

        private const val OLD_ENCLAVE_CLASS_NAME = "com.r3.sgx.core.enclave.Enclave"
        private const val CONCLAVE_ENCLAVE_CLASS_NAME = "com.r3.conclave.enclave.Enclave"
        private const val ENCLAVE_CLASS_ATTRIBUTE = "Enclave-Class"
    }


    @get:InputDirectory
    val inputBinutilsDirectory: DirectoryProperty = objects.directoryProperty()

    @get:InputFile
    val inputJar: RegularFileProperty = objects.fileProperty()

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @get:Internal
    var embeddedJarName: String = "app.jar"

    @get:Internal
    var outputName: String = "app.jar.o"

    private val outputJar: File get() = outputDir.file(embeddedJarName).get().asFile

    @get:OutputFile
    val outputJarObject: Provider<RegularFile> = outputDir.file(outputName)

    override fun sgxAction() {
        enclaveClassSanityCheck()
        val binutilsDirectory = inputBinutilsDirectory.asFile.get()
        inputJar.asFile.get().copyTo(outputJar, overwrite = true)
        project.exec { spec ->
            spec.workingDir(outputDir)
            spec.commandLine(
                    File(binutilsDirectory, "ld-static"),
                    "-r",
                    "-b", "binary",
                    embeddedJarName,
                    "-o", outputJarObject.get().asFile
            )
        }
    }

    private fun enclaveClassSanityCheck() {
        val inputJarUrl = inputJar.asFile.get().toURI().toURL()
        val enclaveClassName = readEnclaveClassName(inputJarUrl)
        URLClassLoader(arrayOf(inputJarUrl), null).use { classLoader ->
            val appEnclaveClass = classLoader.loadRequiredClass(enclaveClassName)
            if (classLoader.loadRequiredClass(OLD_ENCLAVE_CLASS_NAME).isAssignableFrom(appEnclaveClass)) return@use
            if (classLoader.loadRequiredClass(CONCLAVE_ENCLAVE_CLASS_NAME).isAssignableFrom(appEnclaveClass)) return@use
            throw InvalidUserCodeException("Enclave-Class set on manifest ($enclaveClassName) does not extend $CONCLAVE_ENCLAVE_CLASS_NAME")
        }
    }

    private fun ClassLoader.loadRequiredClass(className: String): Class<*> {
        try {
            return loadClass(className)
        } catch (e: ClassNotFoundException) {
            throw InvalidUserCodeException("Class $className could not be found in $inputJar.")
        }
    }
}
