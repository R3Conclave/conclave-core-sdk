package com.r3.conclave.enclave.internal

import com.r3.conclave.common.internal.*
import com.r3.conclave.common.internal.attestation.Attestation
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.PublicKey

abstract class EnclaveHostInterface : CallInterface<HostCallType, EnclaveCallType>() {
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
        initiateOutgoingCall(HostCallType.SET_ENCLAVE_INFO, buffer)
    }

    /**
     * Get a signed quote from the host.
     */
    fun getSignedQuote(report: ByteCursor<SgxReport>): ByteCursor<SgxSignedQuote> {
        val quoteBuffer = initiateOutgoingCallWithReturn(HostCallType.GET_SIGNED_QUOTE, report.buffer)
        return Cursor.slice(SgxSignedQuote, quoteBuffer)
    }

    /**
     * Get quoting enclave info from the host.
     */
    fun getQuotingEnclaveInfo(): ByteCursor<SgxTargetInfo> {
        val infoBuffer = initiateOutgoingCallWithReturn(HostCallType.GET_QUOTING_ENCLAVE_INFO)
        return Cursor.slice(SgxTargetInfo, infoBuffer)
    }

    /**
     * Request an attestation from the host.
     */
    fun getAttestation(): Attestation {
        val buffer = initiateOutgoingCallWithReturn(HostCallType.GET_ATTESTATION)
        return Attestation.getFromBuffer(buffer)
    }

    /**
     * Send a response to the host enclave message handler.
     */
    fun sendEnclaveMessageResponse(response: ByteBuffer) {
        initiateOutgoingCall(HostCallType.CALL_MESSAGE_HANDLER, response)
    }
}
