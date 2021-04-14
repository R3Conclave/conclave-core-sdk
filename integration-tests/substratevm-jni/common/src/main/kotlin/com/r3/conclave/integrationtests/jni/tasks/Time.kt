package com.r3.conclave.integrationtests.jni.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
class Time: GraalJniTask(), Deserializer<Long> {
    override fun run(): ByteArray {
        val now = Instant.now()
        return Json.encodeToString(now.epochSecond).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Long {
        return decode(encoded)
    }
}