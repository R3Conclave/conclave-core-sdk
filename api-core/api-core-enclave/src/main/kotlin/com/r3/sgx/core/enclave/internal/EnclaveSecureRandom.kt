package com.r3.sgx.core.enclave.internal

import java.security.SecureRandomSpi

@Suppress("unused")
// This class is dynamically loaded when constructing Avian security provider.
class EnclaveSecureRandomSpi: SecureRandomSpi() {

    override fun engineGenerateSeed(len: Int): ByteArray {
        val result = ByteArray(len)
        NativeEnclaveApi.getRandomBytes(result, 0, len)
        return result
    }

    override fun engineNextBytes(output: ByteArray) {
        NativeEnclaveApi.getRandomBytes(output, 0, output.size)
    }

    override fun engineSetSeed(input: ByteArray?) {}
}