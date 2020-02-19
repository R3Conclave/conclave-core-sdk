package com.r3.sgx.test.assertion

/**
 * All assertion classes should implement this interface. The host will invoke these functions using reflection.
 */
interface TestAsserter {
    /**
     * @param testResult The serialized test result to be asserted
     */
    fun assertResult(testResult: ByteArray)
}
