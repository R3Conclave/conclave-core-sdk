package com.r3.sgx.core.common.internal.encryption

import com.r3.sgx.core.common.*
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import javax.crypto.spec.SecretKeySpec

/**
 * A base class for both ends of an encrypted handler connection
 */
abstract class EncryptedConnection(val upstream: Sender) {

    /**
     * Model the dynamic part of the connection state and its transition the protocol
     */
     sealed class State {

        abstract fun handle(owner: EncryptedConnection, message: ByteBuffer): State?

        /**
         * Waiting for connection request
         */
        class HandShakingResponder: State() {
            override fun handle(owner: EncryptedConnection, message: ByteBuffer): Connected {
                val protocolId = EncryptionProtocolId.fromInt(message.getInt())
                        ?: throw SecurityException("Unknown protocol ID")
                val peerKeyBytes = ByteArray(message.remaining()).also {
                    message.get(it)
                }
                val keyAgreementProtocol = EncryptedConnection.getKeyAgreementInstance(protocolId)
                val publicKey = keyAgreementProtocol.publicSessionKey
                val signedPublicKey = owner.signKeyAgreementMessage(protocolId, publicKey)
                owner.upstream.send(signedPublicKey.size, Consumer { it.put(signedPublicKey) })
                val sharedKey = keyAgreementProtocol.computeSharedSecret(peerKeyBytes)
                return Connected(protocolId, sharedKey)
            }
        }

        /**
         * Shared key agreement in progress
         */
        class HandShakingInitiator(val protocolId: EncryptionProtocolId,
                                   val sessionKeyAgreement: KeyAgreement): State() {
            override fun handle(owner: EncryptedConnection, message: ByteBuffer): Connected {
                val msg = ByteArray(message.remaining()).also {
                    message.get(it)
                }
                val peerKeyBytes = owner.verifyKeyAgreementMessage(protocolId, msg)
                val sharedKey = sessionKeyAgreement.computeSharedSecret(peerKeyBytes)
                return Connected(protocolId, sharedKey)
            }
        }

        /**
         * Encrypted session established
         */
        class Connected(protocolId: EncryptionProtocolId, sharedKey: ByteArray): State() {

            val inputDecryption: Decryptor = EncryptedConnection.getDecryptorInstance(protocolId, sharedKey)
            val outputEncryption: Encryptor = EncryptedConnection.getEncryptorInstance(protocolId, sharedKey)

            override fun handle(owner: EncryptedConnection, message: ByteBuffer): State? {
                val downstream = owner.downstream ?:
                    throw IllegalStateException("Downstream not set")
                val deciphered = ByteBuffer.wrap(inputDecryption.process(message))
                downstream.onReceive(deciphered)
                return null
            }
        }
    }

    inner class EncryptingSender: LeafSender() {
        private val encryptedSink = lazy {
            isHandShakeCompleted.getNow(null) ?: throw IllegalStateException(
                    "Attempt sending message over encrypted channel before handshaking initiated")
            (state as State.Connected).outputEncryption
        }

        override fun sendSerialized(serializedBuffer: ByteBuffer) {
            encryptedSink.value.process(serializedBuffer, upstream)
        }
    }

    abstract var state: State
    val isHandShakeCompleted = CompletableFuture<Unit>()

    @Synchronized
    fun dispatchToDownstream(input: ByteBuffer) {
        val next = state.handle(this, input)
        if (next != null) {
            state = next
            if (next is State.Connected) {
                isHandShakeCompleted.complete(Unit)
            }
        }
    }

    protected abstract fun signKeyAgreementMessage(id: EncryptionProtocolId, input: ByteArray): ByteArray

    protected abstract fun verifyKeyAgreementMessage(id: EncryptionProtocolId, input: ByteArray): ByteArray

    private var downstream: HandlerConnected<*>? = null

    protected fun <CONNECTION> setDownstream(handler: Handler<CONNECTION>): CONNECTION {
        if (downstream != null) {
            throw IllegalStateException("Downstream already set")
        }
        val connection = handler.connect(EncryptingSender())
        downstream = HandlerConnected(handler, connection)
        return connection
    }

    companion object {

        fun getEncryptorInstance(protocolId: EncryptionProtocolId, sharedSecret: ByteArray): Encryptor {
            return when (protocolId) {
                EncryptionProtocolId.ED25519_AESGCM128 -> {
                    val secretKeySpec = SecretKeySpec(sharedSecret, "AES")
                    EncryptorAESGCM(secretKeySpec)
                }
            }
        }

        fun getDecryptorInstance(protocolId: EncryptionProtocolId, sharedSecret: ByteArray): Decryptor {
            return when (protocolId) {
                EncryptionProtocolId.ED25519_AESGCM128 -> {
                    val secretKeySpec = SecretKeySpec(sharedSecret, "AES")
                    DecryptorAESGCM(secretKeySpec)
                }
            }
        }

        fun getKeyAgreementInstance(protocolId: EncryptionProtocolId): KeyAgreement {
            return when (protocolId) {
                EncryptionProtocolId.ED25519_AESGCM128 -> KeyAgreementED25519()
            }
        }
    }
}