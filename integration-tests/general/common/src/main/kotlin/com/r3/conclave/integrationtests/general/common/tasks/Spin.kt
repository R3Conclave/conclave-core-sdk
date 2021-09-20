package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable

@Serializable
class Spin(val data: ByteArray) : JvmTestTask(), Deserializer<ByteArray> {
    override fun run(context: RuntimeContext): ByteArray {
        context.callHost(data)
        while (true) {
            // Spin
        }
    }

    override fun deserialize(encoded: ByteArray): ByteArray {
        return decode(encoded)
    }
}