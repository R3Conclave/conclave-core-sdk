package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
class Adder(val number: Int) : JvmTestTask(), Deserializer<Int> {
    companion object {
        const val maxCallCountKey = "maxCallCountKey"
    }

    override fun run(context: RuntimeContext): ByteArray {
        val maxCallCount = context.getValue(maxCallCountKey) as Int
        // atomic#1 --> sum
        // atomic#2 --> call count
        val sum = context.atomicIntegerOne.addAndGet(number)
        if (context.atomicIntegerTwo.incrementAndGet() == maxCallCount) {
            return Json.encodeToString(Int.serializer(), sum).toByteArray()
        }

        return Json.encodeToString(Int.serializer(), 0).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Int {
        return decode(encoded)
    }
}

@Serializable
class SetInt(val key: String, val number: Int) : JvmTestTask(), Deserializer<Int> {

    override fun run(context: RuntimeContext): ByteArray {
        val prev = context.getValue(key) as Int? ?: 0
        context.setValue(key, number)
        return Json.encodeToString(Int.serializer(), prev).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Int {
        return decode(encoded)
    }
}