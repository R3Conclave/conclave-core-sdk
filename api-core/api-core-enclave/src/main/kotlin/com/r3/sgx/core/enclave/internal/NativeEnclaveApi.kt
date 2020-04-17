package com.r3.sgx.core.enclave.internal

import com.r3.sgx.core.common.HandlerConnected
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.enclave.EnclaveApi
import java.nio.ByteBuffer
import java.util.*

object NativeEnclaveApi : EnclaveApi {
    /** The singleton instance of the user supplied enclave. */
    private var singletonHandler: HandlerConnected<*>? = null

    /**
     * The ECALL entry point. This code does *not* handle exceptions and must be done e.g. by using [com.r3.sgx.core.enclave.RootEnclave]
     *
     * @param input The chunk of data sent from the host.
     */
    @JvmStatic
    @Suppress("UNUSED")
    fun enclaveEntry(input: ByteArray) {
        val singletonHandler = synchronized(this) {
            this.singletonHandler ?: run {
                // The first ECALL is always the enclave class name, which we only use to instantiate the enclave.
                this.singletonHandler = initialiseEnclave(input)
                null
            }
        }
        singletonHandler?.onReceive(ByteBuffer.wrap(input).asReadOnlyBuffer())
    }

    private fun initialiseEnclave(input: ByteArray): HandlerConnected<*> {
        val enclaveClassName = String(input)
        val enclaveClass = Class.forName(enclaveClassName)
        val handlerConnected = if (Enclave::class.java.isAssignableFrom(enclaveClass)) {
            enclaveClass.asSubclass(Enclave::class.java).newInstance().initialize(this, NativeOcallSender)
        } else {
            val conclaveLoader = ServiceLoader.load(ConclaveLoader::class.java).firstOrNull()
            // If there isn't a ServiceLoader on the classpath assume the app is trying to use the old API.
            requireNotNull(conclaveLoader) { "$enclaveClassName does not extend ${Enclave::class.java.name}" }
            conclaveLoader.loadEnclave(enclaveClass, this, NativeOcallSender)
        }
        return checkNotNull(handlerConnected) { "Unable to initialise enclave" }
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