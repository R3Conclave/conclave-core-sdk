package com.r3.sgx.core.enclave.internal

/**
 * The Enclave JNI. We don't use System.loadLibrary, but instead rely on our custom dlsym to find the relevant symbols.
 */
object Native {
    /** Reads a chunk from the statically linked app jar. Poor man's getResources */
    external fun readAppJarChunk(srcOffset: Long, dest: ByteArray, destOffset: Int, length: Int): Int
    /**
     * Makes an OCALL.
     * @param data The chunk of data to be passed to the ocall.
     */
    external fun jvmOcall(data: ByteArray)

    /**
     * A wrapper of sgx_create_report.
     * sgx_status_t sgx_create_report(const sgx_target_info_t *target_info, const sgx_report_data_t *report_data, sgx_report_t *report)
     */
    external fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray)

    /**
     * A wrapper of sgx_read_rand.
     */
    external fun getRandomBytes(output: ByteArray, offset: Int, length: Int)

    /**
     * Returns whether the enclave is a simulation one.
     */
    external fun isEnclaveSimulation(): Boolean
}
