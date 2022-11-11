package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class GenerateGramineManifest @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "java.manifest.template"
    }

    @get:Input
    val maxThreads: Property<Int> = objects.property(Int::class.java)

    @get:OutputFile
    val manifestFile: RegularFileProperty = objects.fileProperty()

    override fun action() {
        val manifestTemplateFile = temporaryDir.resolve(MANIFEST_TEMPLATE).toPath()
        javaClass.copyResource(MANIFEST_TEMPLATE, manifestTemplateFile)

        // TODO We're relying on gcc, python3, pip3 and jep being installed on the machine that builds the Python
        //  enclave. Rather than documenting all this and expecting the user to have their machine correctly setup, it
        //  is better to embed the conclave-build container to always run when building the enclave, not just for
        //  non-linux. https://r3-cev.atlassian.net/browse/CON-1181

        val architecture = commandWithOutput("gcc", "-dumpmachine").trimEnd()
        val ldPreload = executePython("from sysconfig import get_config_var; " +
                "print(get_config_var('LIBPL') + '/' + get_config_var('LDLIBRARY'))"
        )
        // The location displayed by 'pip3 show jep' is actually of the site/dist-packages dir, not the specific 'jep'
        // dir within it. We assume this is the packages dir for other modules as well. If this assumption is
        // incorrect then we'll need to come up with a better solution.
        val pythonPackagesPath = commandWithOutput("pip3", "show", "jep")
            .splitToSequence("\n")
            .single { it.startsWith("Location: ") }
            .substringAfter("Location: ")

        /**
         * It's possible for a Gramine enclave to launch threads internally that Conclave won't know about!
         * Because of this, we need to add some safety margin.
         */
        val gramineMaxThreads = maxThreads.get() * 2

        commandLine(
            listOf(
                "gramine-manifest",
                "-Djava_home=${System.getProperty("java.home")}",
                "-Darch_libdir=/lib/$architecture",
                "-Dld_preload=$ldPreload",
                "-Dpython_packages_path=$pythonPackagesPath",
                "-Dmax_threads=$gramineMaxThreads",
                manifestTemplateFile.absolutePathString(),
                manifestFile.asFile.get().absolutePath
            )
        )
    }

    private fun executePython(command: String): String = commandWithOutput("python3", "-c", command).trimEnd()
}
