package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CpuFeature
import com.r3.conclave.common.internal.NativeMessageType
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object NativeApi {
    private val enclaveCallInterfaces = ConcurrentHashMap<Long, NativeEnclaveCallInterface>()

    // TODO: Temporary for con 1025
    @JvmStatic
    fun registerEnclaveCallInterface(enclaveId: Long, enclaveCallInterface: NativeEnclaveCallInterface) {
        val previous = enclaveCallInterfaces.putIfAbsent(enclaveId, enclaveCallInterface)
        if (previous != null) {
            throw IllegalStateException("Attempt to re-register call interface for enclave id $enclaveId")
        }
    }

    // TODO: Temporary for CON 1025
    @JvmStatic
    @Suppress("UNUSED")
    fun enclaveToHostCon1025(enclaveId: Long, callTypeID: Short, messageTypeID: Byte, data: ByteBuffer) {
        val enclaveCallInterface = checkNotNull(enclaveCallInterfaces[enclaveId])
        enclaveCallInterface.handleOcall(enclaveId, callTypeID, NativeMessageType.fromByte(messageTypeID), data)
    }

    // TODO: Temporary for CON 1025
    @JvmStatic
    fun hostToEnclaveCon1025(enclaveId: Long, callType: Short, messageType: Byte, data: ByteArray) {
        Native.jvmEcallCon1025(enclaveId, callType, messageType, data)
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
