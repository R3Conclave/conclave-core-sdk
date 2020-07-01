package com.r3.conclave.enclave.internal

import com.r3.conclave.common.OpaqueBytes
import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * A [Handler]/[Sender] pair that sends/receives SealUnseal.
 * [seal] true = seal, false = unseal.
 */
abstract class SealUnsealHandler : Handler<SealUnsealSender> {
    /*
     Should be overridden by the Enclave or Recording Handler and used within its context.
     */
    abstract fun onReceiveUnsealedData(connection: SealUnsealSender, plaintextAndEnvelope: PlaintextAndEnvelope)

    /*
     Should be overridden by the Enclave or Recording Handler and used within its context.
     */
    abstract fun onReceiveSealedData(connection: SealUnsealSender, sealedData: ByteArray)

    final override fun onReceive(connection: SealUnsealSender, input: ByteBuffer) {
        if (input.getBoolean()) {
            val plainTextSize = input.getBytes(input.int)
            val plaintext = OpaqueBytes(plainTextSize)
            val authenticatedDataSize = input.int
            val authenticatedData =
                    if (authenticatedDataSize > 0) OpaqueBytes(input.getRemainingBytes()) else null
            onReceiveUnsealedData(connection, PlaintextAndEnvelope(
                    plaintext,
                    authenticatedData))
        } else {
            onReceiveSealedData(connection, input.getRemainingBytes())
        }
    }

    final override fun connect(upstream: Sender): SealUnsealSender = SealUnsealSender(upstream)
}

class SealUnsealSender(private val upstream: Sender) {
    fun sendUnsealedData(plaintextAndEnvelope: PlaintextAndEnvelope) {
        upstream.send(9 + plaintextAndEnvelope.plaintext.size + (plaintextAndEnvelope.authenticatedData?.size ?: 0),
                Consumer { buffer ->
                    buffer.putBoolean(true)
                            .putInt(plaintextAndEnvelope.plaintext.size)
                            .put(plaintextAndEnvelope.plaintext.bytes)
                            .putInt(plaintextAndEnvelope.authenticatedData?.size ?: 0)
                    plaintextAndEnvelope.authenticatedData?.let { buffer.put(it.bytes) }
                })
    }

    fun sendSealedData(sealedData: ByteArray) {
        upstream.send(1 + sealedData.size, Consumer { buffer ->
            buffer.putBoolean(false).put(sealedData)
        })
    }
}
