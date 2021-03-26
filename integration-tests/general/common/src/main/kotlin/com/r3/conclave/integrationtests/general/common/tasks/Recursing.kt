package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
class Recursing(var remaining: Int) : JvmTestTask(), Deserializer<Int> {
    override fun run(context: RuntimeContext): ByteArray {
        while (remaining > 0) {
            remaining = context.callHost((remaining - 1).toByteArray())!!.toInt()
        }
        return Json.encodeToString(Int.serializer(), 0).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Int {
        return decode(encoded)
    }
}