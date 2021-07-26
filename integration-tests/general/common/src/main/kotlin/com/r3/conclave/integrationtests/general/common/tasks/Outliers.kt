package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.TestResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
class Outliers(val name: String): JvmTestTask(), Deserializer<TestResult> {
    override fun run(context: RuntimeContext): ByteArray {
        var result = TestResult(success = true, message = name)
        try {
            when(name){
                "DivideByZero" -> avian.test.DivideByZero.main(emptyArray())
                "Finalizers" -> avian.test.WeakRef.main(emptyArray())
                "NullPointer" -> avian.test.NullPointer.main(emptyArray())
                "OutOfMemory" -> avian.test.OutOfMemory.main(emptyArray())
                "StackOverflow" -> avian.test.StackOverflow.main(emptyArray())
                else -> { result = TestResult(success = false, message = "test not found: $name") }
            }
        } catch(error: RuntimeException) {
            result = TestResult(success = false, message = error.stackTraceToString())
        }
        return Json.encodeToString(TestResult.serializer(), result).toByteArray()
    }

    override fun deserialize(encoded: ByteArray) : TestResult {
        return decode(encoded)
    }
}
