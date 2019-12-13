package com.r3.sgx.core.common

import com.r3.sgx.core.common.attestation.AttestedSignatureVerifier
import com.r3.sgx.core.common.internal.encryption.EncryptedConnection
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * A [Handler] implementing the client side in a session with an [EncryptionRespondingHandler].
 * @property signatureVerifier Instance of [AttestedSignatureVerifier] used to verify and authenticates messages
 * received from the responding enclave during the encrypted session handshaking.
 * @property protocolId Identifies the encryption protocol.
 */
class EncryptionInitiatingHandler(
        val signatureVerifier: AttestedSignatureVerifier,
        val protocolId: EncryptionProtocolId
) : Handler<EncryptionInitiatingHandler.Connection> {

    override fun connect(upstream: Sender): Connection {
        return ConnectionImpl(upstream)
    }

    override fun onReceive(connection: Connection, input: ByteBuffer) {
        connection.receive(input)
    }

    interface Connection {
        /**
         * Initiate an encrypted session with an handler
         */
        fun <CONNECTION> initiate(handler: Handler<CONNECTION>): CONNECTION

        /**
         * Decipher an encrypted message from upstream and send it to initiating handler
         */
        fun receive(input: ByteBuffer)
    }

    private inner class ConnectionImpl(upstream: Sender)
        : EncryptedConnection(upstream)
        , Connection {

        override var state: State = State.HandShakingInitiator(protocolId, getKeyAgreementInstance(protocolId))

        override fun <CONNECTION> initiate(handler: Handler<CONNECTION>): CONNECTION {
            val currentState = state as? State.HandShakingInitiator
                    ?: throw IllegalStateException("Initiator of encrypted session not in expected state")
            val key = currentState.sessionKeyAgreement.publicSessionKey
            upstream.send(key.size + Int.SIZE_BYTES, Consumer { buffer ->
                buffer.putInt(currentState.protocolId.tag)
                buffer.put(key)
            })
            isHandShakeCompleted.get()
            return setDownstream(handler)
        }

        override fun receive(input: ByteBuffer) {
            super.dispatchToDownstream(input)
        }

        override fun verifyKeyAgreementMessage(id: EncryptionProtocolId, input: ByteArray): ByteArray {
            require(id == EncryptionProtocolId.ED25519_AESGCM128) { "Unsupported protocol" }
            val inputBuffer = ByteBuffer.wrap(input)
            val size = inputBuffer.getInt()
            val peerSessionKey = ByteArray(size).also { inputBuffer.get(it) }
            val enclavePubKeyLen = inputBuffer.getInt()
            val enclavePublicKeyEnc = ByteArray(enclavePubKeyLen).also { inputBuffer.get (it) }
            val attestedEnclaveKey = signatureVerifier.decodeAttestedKey(enclavePublicKeyEnc)
            val signatureLength = inputBuffer.getInt()
            val signature = ByteArray(signatureLength).also { inputBuffer.get(it) }
            signatureVerifier.verify(attestedEnclaveKey, signature, peerSessionKey)
            return peerSessionKey
        }

        override fun signKeyAgreementMessage(id: EncryptionProtocolId, input: ByteArray): ByteArray {
            throw SecurityException("Unsupported operation")
        }
    }
}

