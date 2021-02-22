package com.r3.conclave.integrationtests.jni.tasks

import com.r3.conclave.integrationtests.jni.NativeFunctions
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class Close(val fildes: Int) : GraalJniTask(), Deserializer<Int> {
    override fun run(): ByteArray {
        val ret = NativeFunctions.close(fildes)
        return Json.encodeToString(Int.serializer(), ret).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Int {
        return decode(encoded)
    }
}
