package com.r3.conclave.integrationtests.jni.tasks

import com.r3.conclave.integrationtests.jni.CloseablePointer
import com.r3.conclave.integrationtests.jni.NativeFunctions
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.graalvm.word.WordFactory

@Serializable
data class Write(val fd: Int, val buf: ByteArray, val nbyte: Long) : GraalJniTask(), Deserializer<Long> {
    override fun run(): ByteArray {
        val written = CloseablePointer.allocate(buf.size).use {
            NativeFunctions.write(fd, it.pointer, WordFactory.unsigned(nbyte))
        }
        return Json.encodeToString(Long.serializer(), written).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): Long {
        return decode(encoded)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Write

        if (fd != other.fd) return false
        if (!buf.contentEquals(other.buf)) return false
        if (nbyte != other.nbyte) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fd
        result = 31 * result + buf.contentHashCode()
        result = 31 * result + nbyte.hashCode()
        return result
    }
}
