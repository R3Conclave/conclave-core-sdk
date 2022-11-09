package com.r3.conclave.plugin.enclave.gradle

import com.r3.conclave.common.internal.PluginUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import javax.inject.Inject

/**
 * Take an existing source fat jar containing the enclave code and create a new jar file which has additions made to
 * it. Normally you would just update the shadowJar task to include the additional resource, however it's a bit more
 * complex here because the location of the resource that needs to be added is dependent on the value of the enclave
 * class name. Due to how Gradle works with task configuration it's easier to simply take the existing source fat jar
 * and re-jar it again with the additional resource.
 */
open class EnclaveFatJar @Inject constructor(objects: ObjectFactory) : Jar() {
    init {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        isZip64 = true
        archiveAppendix.set("fat")
    }

    @get:InputFile
    val sourceFatJar: RegularFileProperty = objects.fileProperty()

    @TaskAction
    override fun copy() {
        from(project.zipTree(sourceFatJar))
        super.copy()
    }

    /**
     * In [copy] we need to be able to create a child copy spec so that we can copy the enclave properties file into
     * the specific location based on the enclave's class name. This can't be done during the execution phase,
     * otherwise you the following error: "You cannot add child specs at execution time. Consider configuring this
     * task during configuration time or using a separate task to do the configuration." [EnclaveFatJarConfigure] is
     * that separate task.
     */
    internal fun delayedConfiguration(): EnclaveFatJarConfigure {
        val configureTask = project.tasks.create("configure_$name", EnclaveFatJarConfigure::class.java, this)
        dependsOn(configureTask)
        return configureTask
    }

    open class EnclaveFatJarConfigure @Inject constructor(
        objects: ObjectFactory,
        private val target: EnclaveFatJar
    ) : DefaultTask() {
        @get:InputFile
        val enclaveProperties: RegularFileProperty = objects.fileProperty()

        @get:Input
        val enclaveClassName: Property<String> = objects.property(String::class.java)

        @TaskAction
        fun run() {
            // Create the child copy spec before EnclaveFatJar executes
            target.from(enclaveProperties.get()) { copySpec ->
                copySpec.into(enclaveClassName.get().substringBeforeLast('.').replace('.', '/'))
                copySpec.rename { PluginUtils.ENCLAVE_PROPERTIES }
            }
        }
    }
}
