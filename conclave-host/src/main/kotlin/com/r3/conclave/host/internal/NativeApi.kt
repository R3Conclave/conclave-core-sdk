package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CpuFeature
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object NativeApi {
    private val connectedOcallHandlers = ConcurrentHashMap<Long, HandlerConnected<*>>()

    @JvmStatic
    fun registerOcallHandler(enclaveId: Long, handlerConnected: HandlerConnected<*>) {
        val previous = connectedOcallHandlers.putIfAbsent(enclaveId, handlerConnected)
        if (previous != null) {
            throw IllegalStateException("Attempt to re-register handler for enclave id $enclaveId")
        }
    }

    /**
     * This is called by the native jvm_ocall function. The [ByteBuffer] it passes to this method is a direct one,
     * wrapping some native memory region. This memory is freed after jvm_ocall returns. Therefore reference to the
     * buffer must not to be kept around and any bytes that need to be used later must be copied. This is already the
     * contract for [Handler.onReceive].
     */
    @JvmStatic
    @Suppress("UNUSED")
    fun enclaveToHost(enclaveId: Long, buffer: ByteBuffer) {
        val ocallHandler = checkNotNull(connectedOcallHandlers[enclaveId]) { "Unknown enclave ID $enclaveId" }
        ocallHandler.onReceive(buffer.asReadOnlyBuffer())
    }

    @JvmStatic
    fun hostToEnclave(enclaveId: Long, data: ByteArray) {
        Native.jvmEcall(enclaveId, data)
    }

    /**
     * Retrieve a list of all current CPU features.
     */
    @JvmStatic
    val cpuFeatures: Set<CpuFeature>
        get() {
            val values = EnumSet.noneOf(CpuFeature::class.java)
            val existingFeatures = NativeShared.getCpuFeatures()
            CpuFeature.values().forEach { v ->
                if (existingFeatures and v.feature != 0L)
                    values.add(v)
            }
            return values
        }
}
