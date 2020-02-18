package com.r3.conclave.host.internal

import com.r3.sgx.core.common.ByteCursor
import com.r3.sgx.core.common.SgxSignedQuote

interface AttestationService {
    fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationServiceReportResponse
}

interface AttestationServiceReportResponse {
    val httpResponse: ByteArray
    val signature: ByteArray
    val certificate: String
}
