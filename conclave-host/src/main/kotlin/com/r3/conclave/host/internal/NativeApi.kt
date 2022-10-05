package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.CpuFeature
import com.r3.conclave.common.internal.CallInterfaceMessageType
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object NativeApi {
    private val hostEnclaveInterfaces = ConcurrentHashMap<Long, NativeHostEnclaveInterface>()

    /**
     * Register an enclave call interface with the native API.
     * Each call interface serves as the communication endpoint for one enclave.
     * It serves as the initiator for calls to the enclave, and the handler for calls originating from the enclave.
     *
     * @param enclaveId The ID of the enclave to register the call interface with.
     * @param hostEnclaveInterface An instance of the [HostEnclaveInterface] class to be used for communication with the specified enclave.
     */
    @JvmStatic
    fun registerHostEnclaveInterface(enclaveId: Long, hostEnclaveInterface: NativeHostEnclaveInterface) {
        val previous = hostEnclaveInterfaces.putIfAbsent(enclaveId, hostEnclaveInterface)
        if (previous != null) {
            throw IllegalStateException("Attempt to re-register call interface for enclave id $enclaveId")
        }
    }

    /**
     * This method is the entry point for messages delivered from the enclave to the host.
     * This is part of the low-level communication mechanism used by native enclaves.
     *
     * @param enclaveId The ID of the enclave the message originated from.
     * @param callTypeID The type of call which the message is part of, see [com.r3.conclave.common.internal.EnclaveCallType] and [com.r3.conclave.common.internal.HostCallType].
     * @param messageTypeID The purpose of the message, see [com.r3.conclave.common.internal.CallInterfaceMessageType].
     * @param data A byte buffer containing message data.
     */
    @JvmStatic
    @Suppress("UNUSED")
    fun receiveOcall(enclaveId: Long, callTypeID: Byte, messageTypeID: Byte, data: ByteBuffer) {
        val hostEnclaveInterface = checkNotNull(hostEnclaveInterfaces[enclaveId])
        hostEnclaveInterface.handleOcall(enclaveId, callTypeID, CallInterfaceMessageType.fromByte(messageTypeID), data)
    }

    /**
     * Sends a message from the host to the enclave.
     * This is part of the low-level communication mechanism used by native enclaves.
     *
     * @param enclaveId The ID of the enclave to send the message to.
     * @param callTypeID The type of call which the message is part of, see [com.r3.conclave.common.internal.EnclaveCallType] and [com.r3.conclave.common.internal.HostCallType].
     * @param messageTypeID The purpose of the message, see [com.r3.conclave.common.internal.CallInterfaceMessageType].
     * @param data A byte buffer containing data to send to the enclave.
     */
    @JvmStatic
    fun sendEcall(enclaveId: Long, callTypeID: Byte, messageTypeID: Byte, data: ByteArray) {
        Native.jvmEcall(enclaveId, callTypeID, messageTypeID, data)
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
