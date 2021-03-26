package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.json.Json

@Serializable
class WithState(val data: ByteArray) : JvmTestTask(), Deserializer<ByteArray> {

    val KEY = "state"

    override fun run(context: RuntimeContext): ByteArray {
        var prev = context.getValue(KEY) as String? ?: ""
        val builder = StringBuilder(prev)
        for (index in data) {
            val lookupValue = context.callHost(byteArrayOf(index))!!
            builder.append(String(lookupValue))
        }
        prev = builder.toString()
        context.setValue(KEY, prev)
        return Json.encodeToString(ByteArraySerializer(), prev.toByteArray()).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): ByteArray {
        return decode(encoded)
    }
}