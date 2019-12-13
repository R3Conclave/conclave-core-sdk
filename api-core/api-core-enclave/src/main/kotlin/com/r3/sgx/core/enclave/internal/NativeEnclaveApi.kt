package com.r3.sgx.core.enclave.internal

import com.r3.sgx.core.enclave.*
import java.nio.ByteBuffer
import java.util.jar.JarInputStream

object NativeEnclaveApi : EnclaveApi {
    const val ENCLAVE_CLASS_ATTRIBUTE_NAME = "Enclave-Class"

    override fun getEnclaveClassName(): String = JarInputStream(RawAppJarInputStream()).use { jar ->
        jar.manifest.mainAttributes.getValue(ENCLAVE_CLASS_ATTRIBUTE_NAME)
                ?: throw IllegalStateException("Enclave class not specified. Expected $ENCLAVE_CLASS_ATTRIBUTE_NAME attribute in manifest")
    }

    /** The singleton instance of the user supplied [Enclave]. */
    private val singletonHandler by lazy {
        val enclaveClass = Class.forName(getEnclaveClassName()).let {
            require(Enclave::class.java.isAssignableFrom(it)) {
                "Class specified in manifest $ENCLAVE_CLASS_ATTRIBUTE_NAME does not extend ${Enclave::class.java.name}"
            }
            @Suppress("unchecked_cast")
            it as Class<out Enclave>
        }
        val enclave = enclaveClass.newInstance()
        enclave.initialize(this, NativeOcallSender)
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