package com.r3.conclave.integrationtests.general.tests

import com.r3.conclave.integrationtests.general.common.tasks.Outliers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.RuntimeException

class ExceptionMustBeCaughtTest : JvmTest(threadSafe = false) {
    @ParameterizedTest
    @ValueSource(strings = ["Finalizers", "DivideByZero", "NullPointer"])
    fun `exception is thrown, caught and processed successfully inside an enclave`(name: String) {
        try {
            val action = Outliers(name)
            val result = sendMessage(action)
            if (!result.success) fail(result.message)
            println("Passed: $name")
        } catch (rte: RuntimeException){
            rte.printStackTrace()
            fail(rte.message!!)
        }
    }

    @Disabled("Gradle Test Executor is not happy: exit code 139")
    @ParameterizedTest
    @ValueSource(strings = ["StackOverflow", "OutOfMemory"])
    fun `exceptions might be propagated to the host`(name: String) {
        try {
            // exception thrown inside an enclave,
            // propagated to the host as RuntimeException.
            // no return value.
            val action = Outliers(name)
            sendMessageNoReply(action)
            println("Passed: $name")
        } catch (rte: RuntimeException){
            closeHost = false
            println("RuntimeException: ${rte.message}")
        } catch (rte: Throwable){
            closeHost = false
            println("Throwable: ${rte.message}")
        }
    }

    private fun fail(error: String){
        Assertions.fail<Outliers>(error)
    }
}
