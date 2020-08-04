package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.PlaintextAndEnvelope
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.*

/**
 * A utility that records all calls received into lists.
 */
class SealUnsealRecordingHandler : SealUnsealHandler(), Closeable {
    private val unsealCalls = LinkedList<PlaintextAndEnvelope>()
    private val sealCalls = LinkedList<ByteBuffer>()
    val pollLastReceivedUnsealedData: PlaintextAndEnvelope get() = unsealCalls.pollLast()
    val pollLastReceivedSealedData: ByteBuffer get() = sealCalls.pollLast()

    val size: Int get() = unsealSize + sealSize
    val unsealSize: Int get() = unsealCalls.size
    val sealSize: Int get() = sealCalls.size

    fun clearUnseal() {
        unsealCalls.clear()
    }

    fun clearSeal() {
        sealCalls.clear()
    }

    fun clear() {
        clearUnseal()
        clearSeal()
    }

    fun hasNextUnseal(): Boolean = unsealCalls.isNotEmpty()
    fun hasNextSeal(): Boolean = sealCalls.isNotEmpty()

    override fun onReceiveSealedData(connection: SealUnsealSender, sealedData: ByteArray) {
        sealCalls.addLast(ByteBuffer.wrap(sealedData.clone()))
    }

    override fun onReceiveUnsealedData(connection: SealUnsealSender, plaintextAndEnvelope: PlaintextAndEnvelope) {
        unsealCalls.addLast(plaintextAndEnvelope)
    }

    override fun close() {
        clear()
    }
}
