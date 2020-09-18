package com.r3.conclave.integrationtests.djvm.base

import java.util.function.Function

/**
 * All test classes must implement this interface and have the suffix `EnclaveTest` in their names.
 * The enclave handler will invoke these functions using reflection.
 */
interface EnclaveJvmTest : Function<Any?, Any?>, TestAsserter {
    /**
     * @param output Test output to be serialized
     * @return A serialized representation of the test output
     */
    fun serializeTestOutput(output: Any?) : ByteArray
}
