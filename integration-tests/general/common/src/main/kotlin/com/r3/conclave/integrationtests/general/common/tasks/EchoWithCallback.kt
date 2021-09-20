package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.json.Json

@Serializable
class EchoWithCallback(val data: ByteArray) : JvmTestTask(), Deserializer<ByteArray> {
    override fun run(context: RuntimeContext): ByteArray {
        var echoBack = data
        while (true) {
            val response = context.callHost(echoBack)
            if (response == null) {
                val encodeToString = Json.encodeToString(ByteArraySerializer(), byteArrayOf())
                return encodeToString.toByteArray()
            }
            echoBack = response
        }
    }

    override fun deserialize(encoded: ByteArray): ByteArray {
        return decode(encoded)
    }
}
