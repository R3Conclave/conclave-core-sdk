package com.r3.conclave.integrationtests.jni

import com.r3.conclave.enclave.Enclave
import com.r3.conclave.integrationtests.jni.tasks.GraalJniTask
import com.r3.conclave.integrationtests.jni.tasks.format

class JniEnclave : Enclave() {

    /**
     * The [JniEnclave] deserializes a [GraalJniTask] by creating an instance
     * of the subclass that has been serialized.
     * @param bytes the serialized [GraalJniTask]
     * @return The serialized response for the [GraalJniTask] as a [ByteArray]
     */
    @Override
    override fun receiveFromUntrustedHost(bytes: ByteArray): ByteArray {
        val data = String(bytes)
        val message = format.decodeFromString(GraalJniTask.serializer(), data)
        return message.run()
    }
}