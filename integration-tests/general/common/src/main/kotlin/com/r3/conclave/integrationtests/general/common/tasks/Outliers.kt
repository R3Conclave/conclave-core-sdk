package com.r3.conclave.integrationtests.general.common.tasks

import com.r3.conclave.integrationtests.general.common.TestResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
class Outliers(val name: String): JvmTestTask(), Deserializer<TestResult> {
    override fun run(context: RuntimeContext): ByteArray {
        var result = TestResult(success = false, message = "test not found: $name")
        return Json.encodeToString(TestResult.serializer(), result).toByteArray()
    }

    override fun deserialize(encoded: ByteArray) : TestResult {
        return decode(encoded)
    }
}
