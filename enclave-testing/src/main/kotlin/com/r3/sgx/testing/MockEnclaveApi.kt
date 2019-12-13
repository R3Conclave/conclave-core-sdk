package com.r3.sgx.testing

import com.r3.sgx.core.enclave.Enclave
import com.r3.sgx.core.enclave.EnclaveApi
import java.util.*

class MockEnclaveApi(val enclave: Enclave, private val simulation: Boolean) : EnclaveApi {
    constructor(enclave: Enclave) : this(enclave, false)

    override fun isSimulation(): Boolean = simulation

    override fun isDebugMode(): Boolean = true

    override fun createReport(targetInfoIn: ByteArray?, reportDataIn: ByteArray?, reportOut: ByteArray) {
        throw UnsupportedOperationException("Mock Enclave has no report data")
    }

    override fun getEnclaveClassName(): String {
        return enclave.javaClass.name
    }

    override fun getRandomBytes(output: ByteArray, offset: Int, length: Int) {
        val rng = Random()
        val bytes = ByteArray(length)
        rng.nextBytes(bytes)
        System.arraycopy(bytes, 0, output, offset, length)
    }
}