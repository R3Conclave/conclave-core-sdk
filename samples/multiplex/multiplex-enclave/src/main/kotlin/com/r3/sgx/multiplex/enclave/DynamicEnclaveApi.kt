package com.r3.sgx.multiplex.enclave

import com.r3.sgx.core.common.*
import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.enclave.EnclaveApi
import com.r3.sgx.core.enclave.internal.NativeEnclaveApi.ENCLAVE_CLASS_ATTRIBUTE_NAME
import java.nio.ByteBuffer

class DynamicEnclaveApi(
    private val upstream: Sender,
    private val enclaveJar: EnclaveJar,
    private val master: EnclaveApi
) : EnclaveApi {

    override fun getEnclaveClassName(): String = enclaveJar.className

    /** The singleton instance of the user-supplied [Enclave]. */
    private val singletonHandler: HandlerConnected<*> by lazy {
        val enclaveClass = Class.forName(getEnclaveClassName(), false, enclaveJar.classLoader).let {
            require(Enclave::class.java.isAssignableFrom(it)) {
                "Class specified in manifest $ENCLAVE_CLASS_ATTRIBUTE_NAME does not extend ${Enclave::class.java.name}"
            }
            @Suppress("unchecked_cast")
            it as Class<out Enclave>
        }
        val enclave = enclaveClass.newInstance()
        enclave.initialize(this, upstream)
            ?: throw CreateDynamicEnclaveException("Failed to create dynamic enclave ${enclaveClass.name}")
    }

    fun onReceive(input: ByteBuffer) {
        singletonHandler.onReceive(input)
    }

    override fun isDebugMode(): Boolean = master.isDebugMode()

    override fun isSimulation(): Boolean = master.isSimulation()

    override fun getRandomBytes(output: ByteArray, offset: Int, length: Int) {
        master.getRandomBytes(output, offset, length)
    }

    override fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray) {
    }
}
