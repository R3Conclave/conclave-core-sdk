package com.r3.conclave.jvmtester.api

import java.util.function.Function

/**
 * All test classes must implement this interface and have the suffix `EnclaveTest` in their names.
 * The enclave handler will invoke these functions using reflection.
 */
interface EnclaveJvmTest : Function<Any?, Any?>, TestAsserter {
    /**
     * When the input for a test needs to be serialized, e.g., when it cannot be generated inside the enclave,
     * the test must implement [com.r3.sgx.test.serialization.TestSerializable]
     * @return A serialized version of the input
     */
    @JvmDefault
    fun getTestInput(): ByteArray? {
        return null
    }

    /**
     * @param output Test output to be serialized
     * @return A serialized representation of the test output
     */
    fun serializeTestOutput(output: Any?) : ByteArray
}