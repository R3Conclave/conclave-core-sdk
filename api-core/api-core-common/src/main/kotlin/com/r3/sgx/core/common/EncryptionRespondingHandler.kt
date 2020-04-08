package com.r3.sgx.core.common

import com.r3.conclave.common.internal.SignatureScheme
import com.r3.sgx.core.common.internal.encryption.EncryptedConnection
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.security.KeyPair

/**
 * A [Handler] encrypting communication with a downstream.
 *
 * @property authKeyPair a [KeyPair] used to sign initial handshaking messages from the enclave.
 * @property authSignatureScheme the signature scheme used for signing with [authKeyPair]
 * @property downstream the downstream handler
 */
class EncryptionRespondingHandler(val authKeyPair: KeyPair,
                                  val authSignatureScheme: SignatureScheme,
                                  val downstream: Handler<*>)
    : Handler<EncryptedConnection> {

    override fun connect(upstream: Sender): EncryptedConnection {
        return Connection(upstream)
    }

    override fun onReceive(connection: EncryptedConnection, input: ByteBuffer) {
        connection.dispatchToDownstream(input)
    }

    private inner class Connection(upstream: Sender): EncryptedConnection(upstream) {

        override var state: State = State.HandShakingResponder()

        init {
            setDownstream(downstream)
        }

        override fun signKeyAgreementMessage(id: EncryptionProtocolId, input: ByteArray): ByteArray {
            if (id != EncryptionProtocolId.ED25519_AESGCM128) {
                throw IllegalStateException("Unknown protocol")
            }
            val enclavePrivateKey = authKeyPair.private
            val enclavePublicKey = authKeyPair.public
            val signature = authSignatureScheme.sign(enclavePrivateKey, input)
            val publicKeyEncoded = enclavePublicKey.encoded
            val outputLength = input.size + 3 * Int.SIZE_BYTES + publicKeyEncoded.size + signature.size
            return ByteBuffer.allocate(outputLength).apply {
                putInt(input.size)
                put(input)
                putInt(publicKeyEncoded.size)
                put(publicKeyEncoded)
                putInt(signature.size)
                put(signature)
            }.array()
        }

        override fun verifyKeyAgreementMessage(id: EncryptionProtocolId, input: ByteArray): ByteArray {
            return input
        }
    }
}

