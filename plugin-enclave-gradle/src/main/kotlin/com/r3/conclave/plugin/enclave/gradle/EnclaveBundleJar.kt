package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.internal.PluginUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import javax.inject.Inject

open class EnclaveBundleJar @Inject constructor(objects: ObjectFactory, type: BuildType) : Jar() {
    init {
        group = ConclaveTask.CONCLAVE_GROUP
        description = "Create Conclave bundle for $type mode"
        archiveBaseName.set("enclave-bundle")
        archiveAppendix.set(type.name.lowercase())
    }

    @get:InputFile
    val bundleFile: RegularFileProperty = objects.fileProperty()

    @get:Input
    val fileName: Property<String> = objects.property(String::class.java)

    @get:Input
    val enclaveClassName: Property<String> = objects.property(String::class.java)

    @TaskAction
    override fun copy() {
        from(bundleFile.get())
        into("${PluginUtils.ENCLAVE_BUNDLES_PATH}/${enclaveClassName.get()}")
        rename { fileName.get() }
        super.copy()
    }
}
