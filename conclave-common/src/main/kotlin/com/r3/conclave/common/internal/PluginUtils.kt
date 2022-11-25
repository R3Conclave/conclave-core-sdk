package com.r3.conclave.common.internal

object PluginUtils {
    const val ENCLAVE_BUNDLES_PATH = "com/r3/conclave/enclave/user-bundles"
    const val GRAALVM_BUNDLE_NAME = "graalvm.so"
    const val GRAMINE_BUNDLE_NAME = "gramine.zip"
    const val ENCLAVE_PROPERTIES = "enclave.properties"
    const val GRAMINE_ENCLAVE_JAR = "enclave.jar"
    const val GRAMINE_MANIFEST = "java.manifest"
    const val GRAMINE_SGX_MANIFEST = "java.manifest.sgx"
    const val GRAMINE_SGX_TOKEN = "java.token"
    /** This contains the enclave's [SgxMetadataEnclaveCss], also known as the `SIGSTRUCT`. */
    const val GRAMINE_SIGSTRUCT = "java.sig"
    const val PYTHON_FILE = "enclave.py"
}
