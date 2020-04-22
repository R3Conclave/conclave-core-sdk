package com.r3.conclave.jvmtester.api

import java.io.IOException

/**
 * All tests which need to serialize their input must implement this interface.
 * The enclave handler will invoke these functions using reflection.
 */
interface TestSerializable {
    /**
     * @param data Serialized test input
     * @return An instance of the test input
     */
    @Throws(IOException::class)
    fun deserializeTestInput(data: ByteArray) : Any?
}
