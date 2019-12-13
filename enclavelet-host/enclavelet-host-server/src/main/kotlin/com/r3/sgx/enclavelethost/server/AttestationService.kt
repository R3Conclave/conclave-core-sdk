package com.r3.sgx.enclavelethost.server

import com.r3.sgx.core.common.Cursor
import com.r3.sgx.core.common.SgxSignedQuote
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

interface AttestationService {
    fun requestSignature(signedQuote: Cursor<ByteBuffer, SgxSignedQuote>): AttestationServiceReportResponse
}

interface AttestationServiceReportResponse {
    val httpResponse: ByteArray
    val signature: ByteArray
    val certificate: String
}
