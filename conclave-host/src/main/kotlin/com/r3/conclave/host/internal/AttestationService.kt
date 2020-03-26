package com.r3.conclave.host.internal

import com.r3.sgx.core.common.ByteCursor
import com.r3.sgx.core.common.SgxSignedQuote

/**
 * An attestation service verifies the validity of enclave quotes.
 */
interface AttestationService {
    fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationResponse
}
