package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject
import kotlin.io.path.absolutePathString

open class GenerateGramineManifest @Inject constructor(objects: ObjectFactory) : ConclaveTask() {
    companion object {
        const val MANIFEST_TEMPLATE = "java.manifest.template"
    }

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
        val jepPath = commandWithOutput("pip3", "show", "jep")
            .splitToSequence("\n")
            .single { it.startsWith("Location: ") }
            .substringAfter("Location: ")

        commandLine(
            listOf(
                "gramine-manifest",
                "-Djava_home=${System.getProperty("java.home")}",
                "-Darch_lib_dir=/lib/$architecture",
                "-Dld_preload=$ldPreload",
                "-Djep_path=$jepPath",
                manifestTemplateFile.absolutePathString(),
                manifestFile.asFile.get().absolutePath
            )
        )
    }

    private fun executePython(command: String): String = commandWithOutput("python3", "-c", command).trimEnd()
}
