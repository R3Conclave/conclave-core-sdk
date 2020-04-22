package com.r3.conclave.jvmtester.api

/**
 * All assertion classes should implement this interface. The host will invoke these functions using reflection.
 */
interface TestAsserter {
    /**
     * @param testResult The serialized test result to be asserted
     */
    fun assertResult(testResult: ByteArray)
}
