package com.r3.sgx.plugin.enclave

import com.r3.sgx.plugin.SgxTask
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
        fun readEnclaveletClassName(jar: URL): String {
            val manifest = JarInputStream(jar.openStream()).use { it.manifest }
            return manifest.mainAttributes.getValue(ENCLAVE_CLASS_ATTRIBUTE) ?:
                throw InvalidUserDataException("Attribute '$ENCLAVE_CLASS_ATTRIBUTE' missing from $jar")
        }

        private const val ENCLAVE_CLASS_NAME = "com.r3.sgx.core.enclave.Enclave"
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
            spec.commandLine(File(binutilsDirectory, "ld-static"),
                    "-r",
                    "-b", "binary",
                    embeddedJarName,
                    "-o", outputJarObject.get().asFile
            )
        }
    }

    private fun treatClassNotFoundException(className: String): InvalidUserCodeException {
        return InvalidUserCodeException("Class $className could not be found in $inputJar.")
    }

    private fun enclaveClassSanityCheck() {
        val className = readEnclaveletClassName(inputJar.asFile.get().toURI().toURL())
        URLClassLoader(arrayOf(inputJar.asFile.get().toURI().toURL()), null).use { classLoader ->
            val appEnclaveClass = try {
                classLoader.loadClass(className)
            } catch (e: ClassNotFoundException) { throw treatClassNotFoundException(className) }
            val enclaveClass = try {
                classLoader.loadClass(ENCLAVE_CLASS_NAME)
            } catch (e: ClassNotFoundException) { throw treatClassNotFoundException(ENCLAVE_CLASS_NAME) }
            if (!enclaveClass.isAssignableFrom(appEnclaveClass)) {
                throw InvalidUserCodeException("Enclave-Class set on manifest does not implement $ENCLAVE_CLASS_NAME")
            }
        }
    }
}
