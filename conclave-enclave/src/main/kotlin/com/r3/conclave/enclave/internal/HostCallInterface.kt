package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.attestation.Attestation
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey

abstract class HostCallInterface : CallInitiator<HostCallType>, CallAcceptor<EnclaveCallType>() {
    companion object {
        private const val MISSING_RETURN_VALUE_ERROR_MESSAGE = "Missing host call return buffer"
    }

    /**
     * Send enclave info to the host.
     * TODO: It would be better to return enclave info from the initialise enclave call
     *       but that doesn't work in mock mode at the moment.
     */
    fun setEnclaveInfo(signatureKey: PublicKey, encryptionKeyPair: KeyPair) {
        val encodedSigningKey = signatureKey.encoded                    // 44 bytes
        val encodedEncryptionKey = encryptionKeyPair.public.encoded     // 32 bytes
        val payloadSize = encodedSigningKey.size + encodedEncryptionKey.size
        val buffer = ByteBuffer.wrap(ByteArray(payloadSize)).apply {
            put(encodedSigningKey)
            put(encodedEncryptionKey)
        }
        executeCall(HostCallType.SET_ENCLAVE_INFO, buffer)
    }

    /**
     * Get a signed quote from the host.
     */
    fun getSignedQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBuffer = checkNotNull(executeCall(HostCallType.GET_SIGNED_QUOTE, report.buffer)) {
            MISSING_RETURN_VALUE_ERROR_MESSAGE
        }
        return Cursor.slice(SgxSignedQuote, quoteBuffer)
    }

    /**
     * Get quoting enclave info from the host.
     */
    fun getQuotingEnclaveInfo(): ByteCursor<SgxTargetInfo> {
        val infoBuffer = checkNotNull(executeCall(HostCallType.GET_QUOTING_ENCLAVE_INFO)) {
            MISSING_RETURN_VALUE_ERROR_MESSAGE
        }
        return Cursor.slice(SgxTargetInfo, infoBuffer)
    }

    /**
     * Request an attestation from the host.
     */
    fun getAttestation(): Attestation {
        val buffer = checkNotNull(executeCall(HostCallType.GET_ATTESTATION)) {
            MISSING_RETURN_VALUE_ERROR_MESSAGE
        }
        return Attestation.getFromBuffer(buffer)
    }

    /**
     * Send a response to the host enclave message handler.
     */
    fun sendEnclaveMessageResponse(response: ByteBuffer) {
        executeCall(HostCallType.CALL_MESSAGE_HANDLER, response)
    }
}
