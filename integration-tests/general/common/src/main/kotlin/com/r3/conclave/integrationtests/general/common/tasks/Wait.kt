package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.json.Json

@Serializable
class Wait(val data: ByteArray) : JvmTestTask(), Deserializer<ByteArray> {
    companion object {
        const val PARALLEL_ECALLS = 8
    }

    override fun run(context: RuntimeContext): ByteArray {
        val ecalls = context.atomicIntegerOne

        ecalls.incrementAndGet()
        while (ecalls.get() < PARALLEL_ECALLS) {
            // wait
        }
        synchronized(this) {
            context.callHost(data)
        }

        return Json.encodeToString(ByteArraySerializer(), byteArrayOf()).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): ByteArray {
        return decode(encoded)
    }
}