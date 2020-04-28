package com.r3.sgx.plugin.host

import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import javax.inject.Inject

open class PrepareEnclaveImage @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        private const val ENCLAVE_FILE = "/app/enclave.signed.so"

        private val List<String>.concatenated: String get() = joinToString(separator = " ")

        fun createCommand(javaOptions: List<String>, applicationOptions: List<String>, enclaveClassName: String): List<String> {
            return mutableListOf("java").apply {
                addAll(javaOptions)
                add("-jar")
                add("/app/app.jar")
                addAll(applicationOptions)
                add(ENCLAVE_FILE)
                add(enclaveClassName)
            }
        }
    }

    @get:Input
    val repositoryUrl: Property<String> = objects.property(String::class.java)

    @get:Input
    val baseImageName: Property<String> = objects.property(String::class.java)

    @get:Input
    val tag: Property<String> = objects.property(String::class.java)

    @get:Input
    @get:Optional
    val commandOptions: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    @get:Input
    @get:Optional
    val hostOptions: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    @get:InputFile
    val enclaveObject: RegularFileProperty = objects.fileProperty()

    @get:Input
    val enclaveClassName: Property<String> = objects.property(String::class.java)

    @get:OutputDirectory
    val dockerDir: DirectoryProperty = objects.directoryProperty()

    @get:Internal
    val dockerFile: Provider<RegularFile> = dockerDir.file("Dockerfile")

    @get:Internal
    val imageName: Provider<String> = repositoryUrl.flatMap { url -> baseImageName.map { name -> "$url/$name" }}

    override fun sgxAction() {
        val enclaveObjectFile = enclaveObject.get().asFile
        enclaveObjectFile.copyTo(dockerDir.file(enclaveObjectFile.name).get().asFile, overwrite = true)

        with(dockerFile.get().asFile) {
            writeText("FROM ${imageName.get()}:${tag.get()}${System.lineSeparator()}")
            appendText("COPY ${enclaveObjectFile.name} $ENCLAVE_FILE${System.lineSeparator()}")
            appendText("ENTRYPOINT ${createCommand(commandOptions.get(), hostOptions.get(), enclaveClassName.get()).concatenated}${System.lineSeparator()}")
        }
    }
}