package com.r3.conclave.common.internal

object PluginUtils {
    const val ENCLAVE_BUNDLES_PATH = "com/r3/conclave/enclave/user-bundles"
    const val GRAALVM_BUNDLE_NAME = "graalvm.so"
    const val GRAMINE_DIRECT_BUNDLE_NAME = "gramine-direct.zip"
    const val GRAMINE_SGX_BUNDLE_NAME = "gramine-sgx.zip"
    const val ENCLAVE_PROPERTIES = "enclave.properties"
    const val GRAMINE_ENCLAVE_JAR = "enclave.jar"
    const val GRAMINE_EXECUTABLE = "java"
    const val GRAMINE_MANIFEST = "$GRAMINE_EXECUTABLE.manifest"
    const val GRAMINE_SGX_MANIFEST = "$GRAMINE_EXECUTABLE.manifest.sgx"
    const val GRAMINE_SGX_TOKEN = "$GRAMINE_EXECUTABLE.token"
    const val GRAMINE_SIG = "$GRAMINE_EXECUTABLE.sig"
    const val PYTHON_FILE = "enclave.py"
}
