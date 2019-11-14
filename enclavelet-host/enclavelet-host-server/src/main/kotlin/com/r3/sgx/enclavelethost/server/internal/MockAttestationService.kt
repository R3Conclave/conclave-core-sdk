package com.r3.sgx.enclavelethost.server.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.SgxSignedQuote
import com.r3.sgx.enclavelethost.ias.schemas.QuoteStatus
import com.r3.sgx.enclavelethost.ias.schemas.ReportResponse
import com.r3.sgx.enclavelethost.server.AttestationService
import com.r3.sgx.enclavelethost.server.AttestationServiceReportResponse
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*

/**
 * A mock implementation of [AttestationService] permitting to reproduce the attestation flow in simulation mode
 */
class MockAttestationService : AttestationService {
    override fun requestSignature(signedQuote: Cursor<ByteBuffer, SgxSignedQuote>): AttestationServiceReportResponse {
        val response = ReportResponse(
                id = "MOCK-RESPONSE: ${UUID.randomUUID()}",
                isvEnclaveQuoteStatus = QuoteStatus.OK,
                isvEnclaveQuoteBody = signedQuote[SgxSignedQuote(signedQuote.getBuffer().capacity()).quote].toByteArray(),
                timestamp = Instant.now(),
                version = 3
        ).let(reportResponseSerializeMapper::writeValueAsBytes)

        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(signingKey)
            update(response)
        }.sign()

        return Response(
                httpResponse = response,
                signature = signature,
                certificate = certificate
        )
    }

    companion object {
        private val signingKey = decodePrivateKey(getResourceBytes("mock-as.key"))
        private val certificate = String(getResourceBytes("mock-as-certificate.pem"))
        private val reportResponseSerializeMapper = ReportResponseSerializer.register(ObjectMapper())

        private fun Cursor<ByteBuffer, SgxQuote>.toByteArray(): ByteArray {
            val buffer = getBuffer()
            return ByteArray(buffer.remaining()).also { buffer.get(it) }
        }

        private fun getResourceBytes(name: String): ByteArray {
            val stream = MockAttestationService::class.java.getResourceAsStream("/mock-as/$name")
                    ?: throw IllegalStateException("Cannot find resource file $name")
            return stream.readBytes()
        }

        private fun decodePrivateKey(bytes: ByteArray): PrivateKey {
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
        }
    }

    private class Response(
            override val httpResponse: ByteArray,
            override val signature: ByteArray,
            override val certificate: String
    ) : AttestationServiceReportResponse
}

