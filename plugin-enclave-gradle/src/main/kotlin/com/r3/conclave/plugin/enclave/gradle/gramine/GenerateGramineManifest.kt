package com.r3.conclave.plugin.enclave.gradle.gramine

import com.r3.conclave.plugin.enclave.gradle.ConclaveTask
import com.r3.conclave.plugin.enclave.gradle.LinuxExec
import com.r3.conclave.utilities.internal.copyResource
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputFile
import javax.inject.Inject

open class GenerateGramineManifest @Inject constructor(objects: ObjectFactory, private val linuxExec: LinuxExec) :
    ConclaveTask() {
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

        val architecture = "x86_64-linux-gnu"
        val ldPreload = "/usr/lib/python3.8/config-3.8-x86_64-linux-gnu/libpython3.8.so"
        val pythonPackagesPath = "/usr/local/lib/python3.8/dist-packages"

        linuxExec.exec(
            listOf<String>(
                "gramine-manifest",
                "-Djava_home=${System.getProperty("java.home")}",
                "-Darch_libdir=/lib/$architecture",
                "-Dld_preload=$ldPreload",
                "-Dpython_packages_path=$pythonPackagesPath",
                manifestTemplateFile.toFile().absolutePath,
                manifestFile.asFile.get().absolutePath
            )
        )
    }
}
