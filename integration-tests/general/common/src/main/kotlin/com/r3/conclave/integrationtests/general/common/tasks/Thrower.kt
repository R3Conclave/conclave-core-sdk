package com.r3.conclave.integrationtests.general.common.tasks

import kotlinx.serialization.Serializable

@Serializable
class Thrower : JvmTestTask(), Deserializer<ByteArray> {
    companion object {
        const val CHEERS = "You are all wrong"
    }

    override fun run(context: RuntimeContext): ByteArray {
        throw RuntimeException(CHEERS)
    }

    override fun deserialize(encoded: ByteArray): ByteArray {
        return decode(encoded)
    }
}