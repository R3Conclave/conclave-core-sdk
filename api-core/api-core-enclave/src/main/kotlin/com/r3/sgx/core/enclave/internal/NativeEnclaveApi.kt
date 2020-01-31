package com.r3.sgx.core.enclave.internal

import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.enclave.EnclaveApi
import java.nio.ByteBuffer
import java.util.*
import java.util.jar.JarInputStream

object NativeEnclaveApi : EnclaveApi {
    const val ENCLAVE_CLASS_ATTRIBUTE_NAME = "Enclave-Class"

    override fun getEnclaveClassName(): String {
        val manifest = JarInputStream(RawAppJarInputStream()).use { it.manifest }
        return checkNotNull(manifest.mainAttributes.getValue(ENCLAVE_CLASS_ATTRIBUTE_NAME)) {
            "Enclave class not specified. Expected $ENCLAVE_CLASS_ATTRIBUTE_NAME attribute in manifest"
        }
    }

    /** The singleton instance of the user supplied enclave. */
    private val singletonHandler by lazy {
        val enclaveClass = Class.forName(getEnclaveClassName())
        if (Enclave::class.java.isAssignableFrom(enclaveClass)) {
            enclaveClass.asSubclass(Enclave::class.java).newInstance().initialize(this, NativeOcallSender)
        } else {
            val conclaveLoader = ServiceLoader.load(ConclaveLoader::class.java).firstOrNull()
            // If there isn't a ServiceLoader on the classpath assume the app is trying to use the old API.
            requireNotNull(conclaveLoader) {
                "Class specified in manifest $ENCLAVE_CLASS_ATTRIBUTE_NAME does not extend ${Enclave::class.java.name}"
            }
            conclaveLoader.loadEnclave(enclaveClass, this, NativeOcallSender)
        }
    }

    /**
     * The ECALL entry point. This code does *not* handle exceptions and must be done e.g. by using [com.r3.sgx.core.enclave.RootEnclave]
     *
     * @param input The chunk of data sent from the host.
     */
    @JvmStatic
    @Suppress("UNUSED")
    fun enclaveEntry(input: ByteArray) {
        singletonHandler?.onReceive(ByteBuffer.wrap(input).asReadOnlyBuffer())
    }

    override fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray) {
        Native.createReport(targetInfoIn, reportDataIn, reportOut)
    }

    override fun getRandomBytes(output: ByteArray, offset: Int, length: Int) {
        Native.getRandomBytes(output, offset, length)
    }

    // Static enclave registration
    init {
        EnclaveSecurityProvider.register()
    }
}