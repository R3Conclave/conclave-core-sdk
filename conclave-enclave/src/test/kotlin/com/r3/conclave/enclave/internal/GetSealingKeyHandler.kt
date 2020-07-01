package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.handler.Handler
import com.r3.conclave.common.internal.handler.Sender
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * KeyRequest data holder.
 * @param keyType unsealed data.
 * @param useSigner sealed data.
 */
data class KeyRequest(val keyType: KeyType, val useSigner: Boolean)

/**
 * A [Handler]/[Sender] pair that sends/receives Sealing Key requests.
 */
abstract class GetSealingKeyHandler : Handler<GetSealingKeySender> {
    /*
     Should be overridden by the Recording Handler and used within its context.
     */
    open fun onReceive(connection: GetSealingKeySender, keyRequest: KeyRequest) {
        throw RuntimeException("Invalid call")
    }

    /*
     Should be overridden by the Enclave Handler and used within its context.
     */
    open fun onReceive(connection: GetSealingKeySender, key: ByteArray) {
        throw RuntimeException("Invalid call")
    }

    override fun onReceive(connection: GetSealingKeySender, input: ByteBuffer) {
        if (input.getBoolean() /* is request */) {
            val keyTypeVal = input.int
            onReceive(connection, KeyRequest(
                    keyType = KeyType.values().single { it.value == keyTypeVal },
                    useSigner = input.getBoolean()))
        } else { /* is response */
            onReceive(connection, input.getRemainingBytes())
        }
    }

    final override fun connect(upstream: Sender): GetSealingKeySender {
        return GetSealingKeySender(upstream)
    }
}

class GetSealingKeySender(private val upstream: Sender) {
    fun sendRequest(keyRequest: KeyRequest) {
        upstream.send(6, Consumer { buffer ->
            buffer.putBoolean(true /*request*/).putInt(keyRequest.keyType.value).putBoolean(keyRequest.useSigner)
        })
    }

    fun sendResponse(key: ByteArray) {
        upstream.send(key.size + 1, Consumer { buffer ->
            buffer.putBoolean(false /*response*/).put(key)
        })
    }
}
