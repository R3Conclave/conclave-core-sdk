package com.r3.conclave.host.internal

import com.r3.conclave.common.internal.ByteCursor
import com.r3.conclave.common.internal.SgxSignedQuote
import com.r3.conclave.common.internal.attestation.AttestationResponse

/**
 * An attestation service verifies the validity of enclave quotes.
 */
interface AttestationService {
    fun requestSignature(signedQuote: ByteCursor<SgxSignedQuote>): AttestationResponse
}
