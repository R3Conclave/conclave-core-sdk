package com.r3.conclave.host.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxQuote
import com.r3.sgx.core.common.SgxSignedQuote
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
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
        private val signingKey = readResource("mock-as.key") {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(it.readBytes()))
        }
        private val certificate = readResource("mock-as-certificate.pem") { it.reader().readText() }

        private val reportResponseSerializeMapper = ReportResponseSerializer.register(ObjectMapper())

        private fun Cursor<ByteBuffer, SgxQuote>.toByteArray(): ByteArray {
            val buffer = getBuffer()
            return ByteArray(buffer.remaining()).also { buffer.get(it) }
        }

        private inline fun <R> readResource(name: String, block: (InputStream) -> R): R {
            val stream = checkNotNull(MockAttestationService::class.java.getResourceAsStream("/mock-as/$name")) {
                "Cannot find resource file $name"
            }
            return stream.use(block)
        }
    }

    private class Response(
            override val httpResponse: ByteArray,
            override val signature: ByteArray,
            override val certificate: String
    ) : AttestationServiceReportResponse
}

