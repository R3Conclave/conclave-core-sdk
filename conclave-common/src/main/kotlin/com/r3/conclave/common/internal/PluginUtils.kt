package com.r3.conclave.common.internal

import java.util.*
import java.util.jar.JarFile
import java.util.jar.Manifest

object PluginUtils {
    const val ENCLAVE_BUNDLES_PATH = "com/r3/conclave/enclave/user-bundles"
    const val GRAALVM_BUNDLE_NAME = "graalvm.so"
    const val GRAMINE_BUNDLE_NAME = "gramine.zip"
    const val ENCLAVE_PROPERTIES = "enclave.properties"
    const val GRAMINE_ENCLAVE_JAR = "enclave.jar"
    const val GRAMINE_MANIFEST = "java.manifest"
    const val GRAMINE_SGX_MANIFEST = "java.manifest.sgx"
    const val GRAMINE_SGX_TOKEN = "java.token"
    const val DOCKER_WORKING_DIR = "/project"
    const val JLINK_CUSTOM_JDK_DIRECTORY = "custom_jdk"
    const val SYSTEM_LIB_DIRECTORY = "custom_system_lib"

    /** This contains the enclave's [SgxEnclaveCss], also known as the `SIGSTRUCT`. */
    const val GRAMINE_SIGSTRUCT = "java.sig"
    const val PYTHON_FILE = "enclave.py"

    fun getManifestAttribute(attributeName: String): String {
        val values = PluginUtils::class.java.classLoader
            .getResources(JarFile.MANIFEST_NAME)
            .asSequence()
            .mapNotNullTo(TreeSet()) { it.openStream().use(::Manifest).mainAttributes.getValue(attributeName) }
        return when (values.size) {
            1 -> values.first()
            0 -> throw IllegalStateException("Could not find manifest attribute $attributeName")
            else -> throw IllegalStateException("Found multiple values for manifest attribute $attributeName: $values")
        }
    }
}
