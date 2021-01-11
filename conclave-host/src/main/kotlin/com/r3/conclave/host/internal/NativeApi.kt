package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CpuFeature
import com.r3.conclave.common.internal.handler.HandlerConnected
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.lang.IllegalStateException
import java.util.*

object NativeApi {
    private val connectedOcallHandlers = ConcurrentHashMap<Long, HandlerConnected<*>>()

    @JvmStatic
    fun registerOcallHandler(enclaveId: Long, handlerConnected: HandlerConnected<*>) {
        val previous = connectedOcallHandlers.putIfAbsent(enclaveId, handlerConnected)
        if (previous != null) {
            throw IllegalStateException("Attempt to re-register handler for enclave id $enclaveId")
        }
    }

    // Called by JNI
    // TODO use ByteBuffers directly
    @JvmStatic
    @Suppress("UNUSED")
    fun enclaveToHost(enclaveId: Long, data: ByteArray) {
        val ocallHandler = connectedOcallHandlers[enclaveId] ?:
                throw IllegalStateException("Unknown enclave id $enclaveId")
        ocallHandler.onReceive(ByteBuffer.wrap(data).asReadOnlyBuffer())
    }

    @JvmStatic
    fun hostToEnclave(enclaveId: Long, data: ByteArray) {
        Native.jvmEcall(enclaveId, data)
    }

    /**
     * Retrieve a list of all current CPU features.
     */
    @JvmStatic
    val cpuFeatures: Set<CpuFeature> get() {
        val values = EnumSet.noneOf(CpuFeature::class.java)
        val existingFeatures = NativeShared.getCpuFeatures()
        CpuFeature.values().forEach{ v ->
            if (existingFeatures and v.feature != 0L)
                values.add(v)
        }
        return values
    }
}
