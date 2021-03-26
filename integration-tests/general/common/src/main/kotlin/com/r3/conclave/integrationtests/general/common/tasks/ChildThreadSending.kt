package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.json.Json

@Serializable
class ChildThreadSending : JvmTestTask(), Deserializer<ByteArray> {
    override fun run(context: RuntimeContext): ByteArray {
        threadWithFuture {
            context.callHost("test".toByteArray())
        }.join()
        return Json.encodeToString(ByteArraySerializer(), byteArrayOf()).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): ByteArray {
        return decode(encoded)
    }
}