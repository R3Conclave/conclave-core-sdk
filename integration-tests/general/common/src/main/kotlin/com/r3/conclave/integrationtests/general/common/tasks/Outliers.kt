package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.EnclaveContext
import com.r3.conclave.integrationtests.general.common.TestResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
class Outliers(val name: String) : EnclaveTestAction<TestResult>() {
    override fun run(context: EnclaveContext, isMail: Boolean): TestResult {
        try {
            when (name) {
                "DivideByZero" -> avian.test.DivideByZero.main(emptyArray())
                "Finalizers" -> avian.test.WeakRef.main(emptyArray())
                "NullPointer" -> avian.test.NullPointer.main(emptyArray())
                "OutOfMemory" -> avian.test.OutOfMemory.main(emptyArray())
                "StackOverflow" -> avian.test.StackOverflow.main(emptyArray())
                else -> return TestResult(success = false, message = "test not found: $name")
            }
            return TestResult(success = true, message = name)
        } catch (error: RuntimeException) {
            return TestResult(success = false, message = error.stackTraceToString())
        }
    }

    override fun resultSerializer(): KSerializer<TestResult> = TestResult.serializer()
}
