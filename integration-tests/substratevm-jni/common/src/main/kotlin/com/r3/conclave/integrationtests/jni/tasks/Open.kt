package com.r3.conclave.integrationtests.jni.tasks

import com.r3.conclave.integrationtests.jni.NativeFunctions
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.graalvm.nativeimage.c.type.CTypeConversion


@Serializable
data class Open(val path: String, val oflag: Int): GraalJniTask(), Deserializer<Int> {
    override fun run(): ByteArray {
        val path = CTypeConversion.toCString(path).get()
        val ret = NativeFunctions.open(path, oflag)
        return Json.encodeToString(Int.serializer(), ret).toByteArray()
    }

    override fun deserialize(encoded: ByteArray) : Int {
        return decode(encoded)
    }
}
