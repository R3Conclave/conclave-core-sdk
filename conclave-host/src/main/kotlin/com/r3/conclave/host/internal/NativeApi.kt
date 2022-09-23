package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CpuFeature
import com.r3.conclave.common.internal.NativeMessageType
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.HandlerConnected
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object NativeApi {
    private val connectedOcallHandlers = ConcurrentHashMap<Long, HandlerConnected<*>>()

    private val enclaveCallInterfaces = ConcurrentHashMap<Long, NativeEnclaveCallInterface>()

    @JvmStatic
    fun registerOcallHandler(enclaveId: Long, handlerConnected: HandlerConnected<*>) {
        val previous = connectedOcallHandlers.putIfAbsent(enclaveId, handlerConnected)
        if (previous != null) {
            throw IllegalStateException("Attempt to re-register handler for enclave id $enclaveId")
        }
    }

    // TODO: Temporary for con 1025
    @JvmStatic
    fun registerEnclaveCallInterface(enclaveId: Long, enclaveCallInterface: NativeEnclaveCallInterface) {
        val previous = enclaveCallInterfaces.putIfAbsent(enclaveId, enclaveCallInterface)
        if (previous != null) {
            throw IllegalStateException("Attempt to re-register call interface for enclave id $enclaveId")
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

    // TODO: Temporary for CON 1025
    @JvmStatic
    @Suppress("UNUSED")
    fun enclaveToHostCon1025(enclaveId: Long, callTypeID: Short, messageTypeID: Byte, data: ByteBuffer) {
        val enclaveCallInterface = checkNotNull(enclaveCallInterfaces[enclaveId])
        enclaveCallInterface.handleOcall(enclaveId, callTypeID, NativeMessageType.fromByte(messageTypeID), data)
    }

    @JvmStatic
    fun hostToEnclave(enclaveId: Long, data: ByteArray) {
        Native.jvmEcall(enclaveId, data)
    }

    // TODO: Temporary for CON 1025
    @JvmStatic
    fun hostToEnclaveCon1025(enclaveId: Long, callType: Short, messageType: NativeMessageType, data: ByteArray) {
        Native.jvmEcallCon1025(enclaveId, callType, messageType.toByte(), data)
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
