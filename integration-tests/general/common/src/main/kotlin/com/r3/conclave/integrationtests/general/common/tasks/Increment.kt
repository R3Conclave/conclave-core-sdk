package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
class Increment(val data: Int) : JvmTestTask(), Deserializer<Int> {
    override fun run(context: RuntimeContext): ByteArray {
        return Json.encodeToString(Int.serializer(), data + 1).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Int {
        return decode(encoded)
    }
}

