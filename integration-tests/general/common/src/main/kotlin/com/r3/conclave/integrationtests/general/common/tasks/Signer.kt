package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.json.Json

@Serializable
class Signer(val data: ByteArray) : JvmTestTask(), Deserializer<ByteArray> {
    override fun run(context: RuntimeContext): ByteArray {
        val signature = context.getSigner().run {
            update(data)
            sign()
        }

        val response = writeData {
            writeIntLengthPrefixBytes(context.getPublicKey().encoded)
            writeIntLengthPrefixBytes(signature)
        }

        return Json.encodeToString(ByteArraySerializer(), response).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): ByteArray {
        return decode(encoded)
    }
}

