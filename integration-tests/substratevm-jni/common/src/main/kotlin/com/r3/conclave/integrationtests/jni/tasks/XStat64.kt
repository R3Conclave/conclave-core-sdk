package com.r3.conclave.integrationtests.jni.tasks

import com.r3.conclave.enclave.internal.substratevm.Stat
import com.r3.conclave.integrationtests.jni.NativeFunctions
import com.r3.conclave.integrationtests.jni.responses.XStat64Response
import com.r3.conclave.jvm.enclave.common.internal.testing.MockStat64Data
import com.r3.conclave.jvm.enclave.common.internal.testing.MockTimespecData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.graalvm.nativeimage.StackValue
import org.graalvm.nativeimage.c.type.CTypeConversion

@Serializable
data class XStat64(val ver: Int, val path: String, val buf: MockStat64Data) : GraalJniTask(), Deserializer<XStat64Response> {
    override fun run(): ByteArray {
        val path = CTypeConversion.toCString(path).get()
        val stat64 = StackValue.get(Stat.Stat64::class.java)
        val ret = NativeFunctions.__xstat64(ver, path, stat64)
        val errno = NativeFunctions.__errno_location().read()
        val response = XStat64Response(ret, errno, MockStat64Data(MockTimespecData(stat64.st_mtim().tv_sec(), stat64.st_mtim().tv_nsec())))
        return Json.encodeToString(XStat64Response.serializer(), response).toByteArray()
    }

    override fun deserialize(encoded: ByteArray): XStat64Response {
        return decode(encoded)
    }
}
